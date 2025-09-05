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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
    private final ApplicationEventPublisher eventPublisher; // 이벤트 발행자

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
            if (latestEnd != null && latestEnd.isAfter(now)) { // 잔여기간 존재
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

        if (changeType == PlanChangeType.UPGRADE) {
            return handleUpgrade(currentSubscription, newPlan);
        } else {
            return handleDowngrade(currentSubscription, newPlan);
        }
    }

    /**
     * 업그레이드 처리
     * - 즉시 적용 + 차액 결제
     */
    private MembershipPlanChangeResponseDto handleUpgrade(MembershipSubscription subscription, MembershipPlan newPlan) {
        // 차액 계산
        Integer prorationAmount = calculateProration(subscription, newPlan);
        
        // 차액 결제 처리 (이벤트 발행)
        eventPublisher.publishEvent(new com.ottproject.ottbackend.event.ProrationPaymentRequestedEvent(
            subscription.getUser().getId(), 
            prorationAmount
        ));
        
        // 플랜 즉시 변경
        subscription.setMembershipPlan(newPlan);
        subscription.setNextPlan(null);
        subscription.setPlanChangeScheduledAt(null);
        subscription.setChangeType(null);
        
        // 다음 결제일은 기존 유지 (변경 없음)
        
        return MembershipPlanChangeResponseDto.builder()
                .changeType(PlanChangeType.UPGRADE)
                .effectiveDate(LocalDateTime.now())
                .prorationAmount(prorationAmount)
                .message("플랜이 즉시 업그레이드되었습니다. 차액 " + prorationAmount + "원이 결제되었습니다.")
                .build();
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

    /**
     * 차액 계산 (업그레이드 시)
     * - 남은 기간에 대한 차액을 계산
     */
    private Integer calculateProration(MembershipSubscription subscription, MembershipPlan newPlan) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endAt = subscription.getEndAt();
        
        // 남은 일수 계산
        long remainingDays = ChronoUnit.DAYS.between(now, endAt);
        if (remainingDays <= 0) {
            return 0;
        }
        
        // 현재 플랜과 새 플랜의 일일 가격 차이
        Integer currentDailyPrice = (int) (subscription.getMembershipPlan().getPrice().getAmount() / 30); // 월 가격을 일 가격으로 변환
        Integer newDailyPrice = (int) (newPlan.getPrice().getAmount() / 30);
        Integer dailyDifference = newDailyPrice - currentDailyPrice;
        
        // 차액 = 일일 차이 × 남은 일수
        Integer prorationAmount = (int) (dailyDifference * remainingDays);
        
        return Math.max(0, prorationAmount); // 음수 방지
    }

    /**
     * 멤버십 구독 요청 이벤트 리스너
     * - 결제 성공 시 발행된 이벤트를 처리하여 구독 생성
     */
    @EventListener
    @Transactional
    public void handleMembershipSubscriptionRequested(com.ottproject.ottbackend.event.MembershipSubscriptionRequestedEvent event) {
        try {
            MembershipSubscribeRequestDto dto = new MembershipSubscribeRequestDto();
            dto.planCode = event.getPlanCode();
            subscribe(event.getUserId(), dto);
            log.info("이벤트 기반 멤버십 구독 생성 완료 - userId: {}, planCode: {}", event.getUserId(), event.getPlanCode());
        } catch (Exception e) {
            log.error("이벤트 기반 멤버십 구독 생성 실패 - userId: {}, planCode: {}", event.getUserId(), event.getPlanCode(), e);
        }
    }
}