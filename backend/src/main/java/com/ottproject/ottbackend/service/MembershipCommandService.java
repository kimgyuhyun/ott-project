package com.ottproject.ottbackend.service;
import com.ottproject.ottbackend.dto.MembershipSubscribeRequestDto;
import com.ottproject.ottbackend.dto.MembershipPlanChangeRequestDto;
import com.ottproject.ottbackend.dto.MembershipPlanChangeResponseDto;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.IdempotencyKey; // NEW
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PlanChangeType;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * MembershipCommandService
 *
 * 큰 흐름
 * - 구독 쓰기 흐름(신청/연장/해지)을 처리한다.
 * - 멱등키로 중복 요청을 방지하고, 말일 해지 정책을 적용한다.
 *
 * 메서드 개요
 * - subscribe: 최근 구독 잔여기간 고려하여 시작점을 산정 후 활성 구독 생성/연장
 * - cancel: 자동갱신 off + 말일 해지 예약(멱등 지원)
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MembershipCommandService {
    private final MembershipPlanRepository planRepository; // 플랜 조회(JPA)
    private final MembershipSubscriptionRepository subscriptionRepository; // 구독 변경(JPA)
    private final com.ottproject.ottbackend.repository.IdempotencyKeyRepository idempotencyKeyRepository; // 멱등키 저장소
    private final MembershipNotificationService notificationService; // 알림 메일 서비스

    /**
     * 구독 신청/연장
     * - 실제 서비스 로직 기준(임시 처리가 아니라 도메인 규칙 반영)
     */
    public void subscribe(Long userId, MembershipSubscribeRequestDto req) {
        MembershipPlan plan = planRepository.findByCode(req.planCode) // 플랜 조회
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "플랜이 존재하지 않습니다.")); // 400

        LocalDateTime now = LocalDateTime.now(); // 기준 시각
        var latestOpt = subscriptionRepository.findTopByUser_IdOrderByStartAtDesc(userId); // 최근 구독
        LocalDateTime start = now; // 시작 시각 기본값
        if (latestOpt.isPresent()) { // 최근 구독 존재
            LocalDateTime latestEnd = latestOpt.get().getEndAt(); // 최근 종료일
            // 잔여기간이 있고, 현재 구독이 ACTIVE 상태인 경우에만 연장
            if (latestEnd != null && latestEnd.isAfter(now) && 
                latestOpt.get().getStatus() == MembershipSubscriptionStatus.ACTIVE) {
                start = latestEnd; // 잔여 종료 직후부터
            }
        }
        LocalDateTime end = start.plusMonths(plan.getPeriodMonths()); // 기간 산정

        User user = new User();
        user.setId(userId);
        MembershipSubscription sub = MembershipSubscription.createSubscription( // 엔티티 생성
                user, // FK 바인딩(프록시)
                plan, // 플랜 설정
                start, // 시작
                end // 종료
        );
        sub.setNextBillingAt(end); // 다음 청구 앵커 // 빌드

        subscriptionRepository.save(sub); // 저장
    }

    /**
     * 플랜 변경 예약 취소
     * - nextPlan/planChangeScheduledAt/changeType 를 초기화하여 전환 예약을 해제한다.
     */
    public void cancelScheduledPlanChange(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        MembershipSubscription currentSubscription = subscriptionRepository.findActiveEffectiveByUser(
                userId, MembershipSubscriptionStatus.ACTIVE, now
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 구독이 없습니다."));

        if (currentSubscription.getNextPlan() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "예약된 플랜 변경이 없습니다.");
        }

        currentSubscription.setNextPlan(null);
        currentSubscription.setPlanChangeScheduledAt(null);
        currentSubscription.setChangeType(null);
    }

    /**
     * 구독 해지
     * - 말일 해지만 지원: 다음 결제만 중단, 만료일까지 혜택 유지
     */
    public void cancel(Long userId, com.ottproject.ottbackend.dto.MembershipCancelMembershipRequestDto req) {
        // 멱등 키 검증(선택 입력)
        if (req != null && req.idempotencyKey != null && !req.idempotencyKey.isBlank()) {
            var exists = idempotencyKeyRepository.findByKeyValue(req.idempotencyKey);
            if (exists.isPresent()) {
                return; // 이미 처리됨: 멱등 보장
            }
        }
        LocalDateTime now = LocalDateTime.now(); // 기준 시각
        MembershipSubscription sub = subscriptionRepository.findActiveEffectiveByUser( // 유효 구독 단건
                userId, MembershipSubscriptionStatus.ACTIVE, now
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 구독이 없습니다.")); // 400

        sub.setAutoRenew(false); // 자동갱신 중단

        // 말일 해지 예약만 수행 (상태 ACTIVE 유지, 만료일까지 혜택 유지)
        sub.setCancelAtPeriodEnd(true);

        // 알림: 말일 해지 예약 안내 메일 발송
        notificationService.sendCancelAtPeriodEnd(sub.getUser(), sub);

        // 멱등 키 저장
        if (req != null && req.idempotencyKey != null && !req.idempotencyKey.isBlank()) {
            var key = IdempotencyKey.createIdempotencyKey(
                    req.idempotencyKey,
                    "membership.cancel",
                    null
            );
            idempotencyKeyRepository.save(key);
        }
    }

    /**
     * 멤버십 정기결제 다시 시작
     * - autoRenew를 true로 변경하고 cancelAtPeriodEnd를 false로 변경
     * - 해지 예약을 취소하고 정기결제를 재개한다
     */
    public void resume(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        MembershipSubscription sub = subscriptionRepository.findActiveEffectiveByUser(
                userId, MembershipSubscriptionStatus.ACTIVE, now
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 구독이 없습니다."));

        // 해지 예약 상태인지 확인
        if (!sub.isCancelAtPeriodEnd()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "해지 예약된 멤버십이 아닙니다.");
        }

        sub.setAutoRenew(true); // 자동갱신 재시작
        sub.setCancelAtPeriodEnd(false); // 말일 해지 예약 해제

        // 알림: 정기결제 재시작 안내 메일 발송
        notificationService.sendResumeNotification(sub.getUser(), sub);
    }

    /**
     * 멤버십 플랜 변경
     * - 업그레이드: 즉시 적용 + 차액 결제
     * - 다운그레이드: 다음 결제일부터 적용
     */
    public MembershipPlanChangeResponseDto changePlan(Long userId, MembershipPlanChangeRequestDto request) {
        // 현재 활성 구독 조회
        LocalDateTime now = LocalDateTime.now();
        MembershipSubscription currentSubscription = subscriptionRepository.findActiveEffectiveByUser(
                userId, MembershipSubscriptionStatus.ACTIVE, now
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 구독이 없습니다."));

        // 새로운 플랜 조회
        MembershipPlan newPlan = planRepository.findByCode(request.getNewPlanCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "플랜이 존재하지 않습니다."));

        // 같은 플랜인지 확인
        if (currentSubscription.getMembershipPlan().getId().equals(newPlan.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재와 같은 플랜입니다.");
        }

        // 변경 유형 판단 (가격 기준)
        PlanChangeType changeType = newPlan.getPrice().getAmount() > currentSubscription.getMembershipPlan().getPrice().getAmount() 
                ? PlanChangeType.UPGRADE 
                : PlanChangeType.DOWNGRADE;

        // 업그레이드는 차액을 실제로 청구하는 경로가 이 API 에 없다. 여기서 플랜을 바꿔주면
        // 결제 없이 상위 플랜을 받아내는 뒷문이 된다(프론트도 업그레이드는 차액 결제 API 를 쓴다).
        if (changeType == PlanChangeType.UPGRADE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "업그레이드는 차액 결제 API(/api/proration-payments)를 사용해야 합니다.");
        }
        return handleDowngrade(currentSubscription, newPlan);
    }

    /**
     * 다운그레이드 처리
     * - 다음 결제일부터 적용
     */
    private MembershipPlanChangeResponseDto handleDowngrade(MembershipSubscription subscription, MembershipPlan newPlan) {
        // 다음 결제일부터 적용하도록 예약
        subscription.setNextPlan(newPlan);
        subscription.setPlanChangeScheduledAt(subscription.getNextBillingAt());
        subscription.setChangeType(PlanChangeType.DOWNGRADE);
        
        return MembershipPlanChangeResponseDto.builder()
                .changeType(PlanChangeType.DOWNGRADE)
                .effectiveDate(subscription.getNextBillingAt())
                .prorationAmount(null)
                .message("다음 결제일(" + subscription.getNextBillingAt().toLocalDate() + ")부터 " + 
                        newPlan.getName() + " 플랜이 적용됩니다.")
                .build();
    }

}