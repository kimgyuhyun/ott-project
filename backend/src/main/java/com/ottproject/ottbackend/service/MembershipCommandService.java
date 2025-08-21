package com.ottproject.ottbackend.service;
import com.ottproject.ottbackend.dto.MembershipSubscribeRequestDto;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.IdempotencyKey; // NEW
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import lombok.RequiredArgsConstructor;
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
            if (latestEnd != null && latestEnd.isAfter(now)) { // 잔여기간 존재
                start = latestEnd; // 잔여 종료 직후부터
            }
        }
        LocalDateTime end = start.plusMonths(plan.getPeriodMonths()); // 기간 산정

        MembershipSubscription sub = MembershipSubscription.builder() // 엔티티 생성
                .user(User.builder().id(userId).build()) // FK 바인딩(프록시)
                .membershipPlan(plan) // 플랜 설정
                .status(MembershipSubscriptionStatus.ACTIVE) // 활성(자동갱신 전제)
                .startAt(start) // 시작
                .endAt(end) // 종료
                .autoRenew(true) // 자동갱신 ON
                .cancelAtPeriodEnd(false) // 말일 해지 예약 해제
                .canceledAt(null) // 해지 확정 시각 초기화
                .nextBillingAt(end) // 다음 청구 앵커
                .retryCount(0) // 재시도 초기화
                .maxRetry(3) // 최대 재시도 3회
                .build(); // 빌드

        subscriptionRepository.save(sub); // 저장
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
            var key = IdempotencyKey.builder()
                    .keyValue(req.idempotencyKey)
                    .purpose("membership.cancel")
                    .createdAt(now)
                    .build();
            idempotencyKeyRepository.save(key);
        }
    }
}