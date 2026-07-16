package com.ottproject.ottbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.dto.PaymentSucceededEventDto;
import com.ottproject.ottbackend.dto.ProrationPaymentRequestDto;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.OutboxEvent;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.OutboxEventRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ProrationPaymentService
 *
 * 큰 흐름
 * - 차액 결제 전용 서비스로 일반 결제와 분리된 로직을 처리한다.
 * - 차액 계산, 결제 세션 생성, 결제 완료 처리를 담당한다.
 *
 * 메서드 개요
 * - createProrationCheckout: 차액 결제 세션 생성
 * - completeProrationPayment: 차액 결제 완료 처리
 * - calculateProrationAmount: 차액 계산
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ProrationPaymentService {
    private final MembershipPlanRepository planRepository;
    private final MembershipSubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway; // 아임포트 재검증(무단 업그레이드 차단)
    private final OutboxEventRepository outboxEventRepository; // 아웃박스 이벤트 리포지토리(영수증 메일 등 부수효과 발행)
    private final ObjectMapper objectMapper; // 이벤트 페이로드 JSON 직렬화

    // 테스트 결제 금액(원). 0이면 실제 차액으로 결제(메인 결제와 동일 규칙 → 재검증 통과)
    @Value("${payments.test-amount:0}")
    private long testAmount;

    /**
     * 차액 결제 세션 생성
     * - 현재 구독과 대상 플랜 간의 차액을 계산하여 결제 세션을 생성한다.
     */
    public Map<String, Object> createProrationCheckout(Long userId, ProrationPaymentRequestDto request) {
        // 현재 활성 구독 조회
        LocalDateTime now = LocalDateTime.now();
        MembershipSubscription currentSubscription = subscriptionRepository.findActiveEffectiveByUser(
                userId, MembershipSubscriptionStatus.ACTIVE, now
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 구독이 없습니다."));

        // 대상 플랜 조회
        MembershipPlan targetPlan = planRepository.findByCode(request.getPlanCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "플랜이 존재하지 않습니다."));

        // 업그레이드인지 확인
        if (targetPlan.getPrice().getAmount() <= currentSubscription.getMembershipPlan().getPrice().getAmount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업그레이드만 차액 결제가 가능합니다.");
        }

        // 차액 계산 (구독 조회에 쓴 기준 시각을 그대로 사용)
        Integer prorationAmount = calculateProrationAmount(currentSubscription, targetPlan, now);
        
        if (prorationAmount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "차액이 없습니다.");
        }

        // 실제 청구 금액: 테스트 환경에서는 test-amount(예: 1원), 운영에서는 계산된 차액.
        // 메인 결제와 동일한 금액 규칙을 써야 아임포트 재검증(verifyPaymentStatus)이 통과한다.
        long chargeAmount = (testAmount > 0 ? testAmount : (long) prorationAmount);

        // 결제 엔티티 생성
        // merchant_uid는 아임포트 정책상 최대 40자 → "proration_"(10) + 하이픈 제거 UUID 30자 = 40자로 고정한다.
        // (초과 시 아임포트가 40자로 잘라 반환하여 webhook 조회/재검증에서 merchant_uid 불일치가 발생했음)
        String providerSessionId = "proration_" + UUID.randomUUID().toString().replace("-", "").substring(0, 30);
        User user = new User();
        user.setId(userId);
        Payment payment = Payment.createPendingPayment(
                user,
                targetPlan,
                com.ottproject.ottbackend.enums.PaymentProvider.IMPORT,
                providerSessionId,
                new com.ottproject.ottbackend.entity.Money(chargeAmount, "KRW")
        );
        payment.setDescription("플랜 업그레이드 차액 결제");
        payment.setMetadata("{\"type\":\"proration\",\"currentPlanCode\":\"" + 
            currentSubscription.getMembershipPlan().getCode() + 
            "\",\"targetPlanCode\":\"" + targetPlan.getCode() + 
            "\",\"paymentService\":\"" + (request.getPaymentService() != null ? request.getPaymentService() : "kakaopay") + "\"}");

        paymentRepository.save(payment);

        // 아임포트 사전 등록(prepare): merchant_uid에 청구 금액을 고정(메인 결제와 동일한 검증 경로 확보)
        if (paymentGateway instanceof ImportPaymentGateway) {
            ((ImportPaymentGateway) paymentGateway).prepare(providerSessionId, chargeAmount);
        }

        // PG 설정
        String pg = getPgByPaymentService(request.getPaymentService());

        Map<String, Object> response = new HashMap<>();
        response.put("paymentId", payment.getId());
        response.put("providerSessionId", providerSessionId);
        response.put("amount", chargeAmount);
        response.put("pg", pg);
        response.put("redirectUrl", null); // 차액 결제는 리다이렉트 없음

        log.info("차액 결제 세션 생성 완료 - userId: {}, paymentId: {}, amount: {}", 
                userId, payment.getId(), prorationAmount);

        return response;
    }

    /**
     * 차액 결제 완료 처리
     * - 결제 성공 시 플랜을 즉시 변경하고 구독을 업데이트한다.
     */
    public Map<String, Object> completeProrationPayment(Long userId, Long paymentId, String impUid) {
        // 결제 조회
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다."));

        // 사용자 확인
        if (!payment.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
        }

        // 결제 상태 확인
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 처리된 결제입니다.");
        }

        // 아임포트 API로 결제 재검증(클라 호출 자체를 신뢰하지 않음 → 결제 없이 무단 업그레이드 차단)
        if (impUid == null || impUid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imp_uid가 필요합니다.");
        }
        long expectedAmount = (payment.getPrice() != null ? payment.getPrice().getAmount() : 0L);
        boolean valid = false;
        if (paymentGateway instanceof ImportPaymentGateway) {
            valid = ((ImportPaymentGateway) paymentGateway)
                    .verifyPaymentStatus(impUid, payment.getProviderSessionId(), expectedAmount);
        }
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제 검증에 실패했습니다. (PG 재검증 불일치)");
        }

        // 결제 성공 처리
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAt(LocalDateTime.now());
        payment.setCompletedAt(LocalDateTime.now());
        payment.setProviderPaymentId(impUid);
        paymentRepository.save(payment);

        // 플랜 변경 처리
        String metadata = payment.getMetadata();
        String targetPlanCode = extractTargetPlanCodeFromMetadata(metadata);
        MembershipPlan targetPlan = planRepository.findByCode(targetPlanCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "대상 플랜을 찾을 수 없습니다."));

        // 현재 구독 조회
        LocalDateTime now = LocalDateTime.now();
        MembershipSubscription currentSubscription = subscriptionRepository.findActiveEffectiveByUser(
                userId, MembershipSubscriptionStatus.ACTIVE, now
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 구독이 없습니다."));

        // 플랜 즉시 변경
        currentSubscription.setMembershipPlan(targetPlan);
        currentSubscription.setNextPlan(null);
        currentSubscription.setPlanChangeScheduledAt(null);
        currentSubscription.setChangeType(null);
        subscriptionRepository.save(currentSubscription);

        // [Kafka/Outbox] 영수증 메일 등 부수효과를 일반 결제와 동일하게 아웃박스 경유로 발행한다.
        // - 일반 결제(PaymentCommandService.markSucceededAndProvision)와 동일한 이벤트/토픽을 적재해
        //   OutboxPublisher → payment.succeeded → PaymentEventConsumer의 영수증 메일 경로로 수렴시킨다.
        // - 결제 확정/플랜 변경과 "같은 트랜잭션"(@Transactional)이므로 적재 실패 시 예외를 전파해 함께 롤백한다.
        try {
            PaymentSucceededEventDto evt = new PaymentSucceededEventDto(
                    UUID.randomUUID().toString(), // 이벤트 고유 식별자(컨슈머 멱등 키)
                    payment.getId(),
                    userId,
                    targetPlan.getCode(), // 업그레이드된 플랜 코드(영수증 한글 표기는 컨슈머에서 코드로 매핑)
                    payment.getPrice() != null ? payment.getPrice().getAmount() : null, // 차액 청구액
                    payment.getPaidAt()
            );
            OutboxEvent outbox = OutboxEvent.create(
                    "Payment", // aggregateType
                    String.valueOf(payment.getId()), // aggregateId
                    "PaymentSucceeded", // eventType
                    "payment.succeeded", // topic
                    evt.getEventId(), // eventId
                    objectMapper.writeValueAsString(evt) // payload(JSON)
            );
            outboxEventRepository.save(outbox);
            log.info("차액 결제 아웃박스 적재 완료 - eventId: {}, paymentId: {}", evt.getEventId(), payment.getId());
        } catch (Exception e) {
            // 아웃박스 적재는 결제 확정/플랜 변경과 원자적이어야 한다(영수증 메일 유실 방지). 실패 시 함께 롤백한다.
            log.error("차액 결제 아웃박스 이벤트 적재 실패 - paymentId: {}", payment.getId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "아웃박스 이벤트 적재 실패: " + e.getMessage(), e);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("paymentId", paymentId);
        response.put("planChangeResult", Map.of(
            "changeType", "UPGRADE",
            "effectiveDate", now.toString(),
            "prorationAmount", payment.getPrice().getAmount(),
            "message", "플랜이 즉시 업그레이드되었습니다."
        ));

        log.info("차액 결제 완료 처리 완료 - userId: {}, paymentId: {}, targetPlan: {}", 
                userId, paymentId, targetPlanCode);

        return response;
    }

    /**
     * 차액 계산
     * - 남은 기간에 대한 차액을 계산한다.
     * - 기준 시각(now)을 파라미터로 받는 순수 함수: 내부에서 LocalDateTime.now() 를 부르면
     *   시간을 고정할 수 없어 결정적 테스트가 불가능하다. 테스트 접근을 위해 package-private.
     */
    Integer calculateProrationAmount(MembershipSubscription subscription, MembershipPlan targetPlan, LocalDateTime now) {
        LocalDateTime endAt = subscription.getEndAt();

        // 무기한 구독(endAt=null)은 남은 일수를 정의할 수 없어 차액을 계산하지 않는다.
        // findActiveEffectiveByUser 가 "s.endAt is null or s.endAt >= :now" 로 무기한 구독을 허용하므로
        // 이 값이 실제로 도달한다(가드가 없으면 ChronoUnit.DAYS.between(now, null) 에서 NPE → 500).
        // 만료 케이스와 동일하게 0을 반환하면 호출부가 "차액이 없습니다"(400)로 정상 거절한다.
        if (endAt == null) {
            return 0;
        }

        // 남은 일수 계산
        long remainingDays = ChronoUnit.DAYS.between(now, endAt);
        if (remainingDays <= 0) {
            return 0;
        }
        
        // 현재 플랜과 새 플랜의 일일 가격 차이
        Integer currentDailyPrice = (int) (subscription.getMembershipPlan().getPrice().getAmount() / 30);
        Integer newDailyPrice = (int) (targetPlan.getPrice().getAmount() / 30);
        Integer dailyDifference = newDailyPrice - currentDailyPrice;
        
        // 차액 = 일일 차이 × 남은 일수
        Integer prorationAmount = (int) (dailyDifference * remainingDays);
        
        return Math.max(0, prorationAmount);
    }

    /**
     * 메타데이터에서 대상 플랜 코드 추출
     */
    private String extractTargetPlanCodeFromMetadata(String metadata) {
        try {
            // 간단한 JSON 파싱 (실제로는 Jackson ObjectMapper 사용 권장)
            String targetPlanCodeKey = "\"targetPlanCode\":\"";
            int startIndex = metadata.indexOf(targetPlanCodeKey);
            if (startIndex == -1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메타데이터에서 대상 플랜 코드를 찾을 수 없습니다.");
            }
            startIndex += targetPlanCodeKey.length();
            int endIndex = metadata.indexOf("\"", startIndex);
            if (endIndex == -1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메타데이터 형식이 올바르지 않습니다.");
            }
            return metadata.substring(startIndex, endIndex);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메타데이터 파싱에 실패했습니다.");
        }
    }

    /**
     * 결제 서비스에 따른 PG 설정
     */
    private String getPgByPaymentService(String paymentService) {
        if (paymentService == null) {
            return "kakaopay.TC0ONETIME";
        }
        
        switch (paymentService.toLowerCase()) {
            case "kakao":
                return "kakaopay.TC0ONETIME";
            case "toss":
                return "tosspay.TC0ONETIME";
            case "nice":
                return "nice.TC0ONETIME";
            default:
                return "kakaopay.TC0ONETIME";
        }
    }
}
