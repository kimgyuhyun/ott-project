package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.ProrationPaymentRequestDto;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        // 차액 계산
        Integer prorationAmount = calculateProrationAmount(currentSubscription, targetPlan);
        
        if (prorationAmount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "차액이 없습니다.");
        }

        // 결제 엔티티 생성
        String providerSessionId = "proration_" + UUID.randomUUID().toString();
        Payment payment = Payment.builder()
                .user(User.builder().id(userId).build())
                .membershipPlan(targetPlan)
                .provider(com.ottproject.ottbackend.enums.PaymentProvider.IMPORT)
                .price(new com.ottproject.ottbackend.entity.Money((long) prorationAmount, "KRW"))
                .status(PaymentStatus.PENDING)
                .providerSessionId(providerSessionId)
                .description("플랜 업그레이드 차액 결제")
                .metadata("{\"type\":\"proration\",\"currentPlanCode\":\"" + 
                    currentSubscription.getMembershipPlan().getCode() + 
                    "\",\"targetPlanCode\":\"" + targetPlan.getCode() + 
                    "\",\"paymentService\":\"" + (request.getPaymentService() != null ? request.getPaymentService() : "kakaopay") + "\"}")
                .build();

        paymentRepository.save(payment);

        // PG 설정
        String pg = getPgByPaymentService(request.getPaymentService());

        Map<String, Object> response = new HashMap<>();
        response.put("paymentId", payment.getId());
        response.put("providerSessionId", providerSessionId);
        response.put("amount", prorationAmount);
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
    public Map<String, Object> completeProrationPayment(Long userId, Long paymentId) {
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

        // 결제 성공 처리
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAt(LocalDateTime.now());
        payment.setCompletedAt(LocalDateTime.now());
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
     */
    private Integer calculateProrationAmount(MembershipSubscription subscription, MembershipPlan targetPlan) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endAt = subscription.getEndAt();
        
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
