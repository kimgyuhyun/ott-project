package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PlanChangeType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 사용자 구독 엔티티
 *
 * 큰 흐름
 * - 사용자의 플랜/상태/기간/갱신 정책을 관리한다.
 * - 자동갱신/말일해지/해지확정/다음결제 기준 시점을 추적한다.
 * - 결제 재시도(dunning) 현황을 보관한다.
 *
 * 필드 개요
 * - id/user/membershipPlan: 식별/소유자/플랜
 * - status: 구독 상태(ACTIVE/PAST_DUE/CANCELED 등)
 * - startAt/endAt: 기간
 * - autoRenew/cancelAtPeriodEnd/canceledAt/nextBillingAt: 정책/일정
 * - retryCount/maxRetry/lastRetryAt/lastError*: dunning 상태
 * - nextPlan/planChangeScheduledAt/changeType: 플랜 변경 예약 정보
 */
@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscription_user", columnList = "user_id") // 사용자 인덱스
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MembershipSubscription { // 멤버쉽 구독
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) // user_id 컬럼 매핑
    private User user; // 사용자 id

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false) // plan_id 컬럼 매핑
    private MembershipPlan membershipPlan; // 플랜 id

    @Enumerated(EnumType.STRING) // 문자열 ENUM 보관
    @Column(nullable = false)
    private MembershipSubscriptionStatus status; // 상태

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt; // 시작 시각

    @Column(name = "end_at", nullable = true)
    private LocalDateTime endAt; // 종료 시각(null=무기한)

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew; // 자동 갱신 여부

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd; // 말일 해지 예약 여부

    @Column(name = "canceled_at", nullable = true) // 해지 확정 시각 기록
    private LocalDateTime canceledAt; // 해지 확정 시각(상태 CANCELED 전환 시점)

    @Column(name = "next_billing_at", nullable = true)
    private LocalDateTime nextBillingAt; // 다음 결제(갱신) 기준 시점

    // ===== 정기결제 재시도(dunning) 정책 필드 =====
    @Column(name = "retry_count", nullable = false)
    private int retryCount; // 현재까지 재시도 횟수(소프트 디클라인 기준)

    @Column(name = "max_retry", nullable = false)
    private int maxRetry; // 최대 재시도 횟수(기본 3)

    @Column(name = "last_retry_at", nullable = true)
    private LocalDateTime lastRetryAt; // 마지막 재시도 시각

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode; // 마지막 실패 코드(게이트웨이)

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage; // 마지막 실패 메시지(게이트웨이)

    // ===== 플랜 변경 예약 필드 =====
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_plan_id")
    private MembershipPlan nextPlan; // 다음 결제일부터 적용될 플랜

    @Column(name = "plan_change_scheduled_at")
    private LocalDateTime planChangeScheduledAt; // 플랜 변경 예약일시

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type")
    private PlanChangeType changeType; // 변경 유형 (UPGRADE/DOWNGRADE)

    // ===== 정적 팩토리 메서드 =====

    /**
     * 멤버십 구독 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 사용자
     * @param membershipPlan 멤버십 플랜
     * @param startAt 시작 시각
     * @param endAt 종료 시각
     * @return 생성된 MembershipSubscription 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static MembershipSubscription createSubscription(User user, MembershipPlan membershipPlan, 
                                                           LocalDateTime startAt, LocalDateTime endAt) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (membershipPlan == null) {
            throw new IllegalArgumentException("멤버십 플랜은 필수입니다.");
        }
        if (startAt == null) {
            throw new IllegalArgumentException("시작 시각은 필수입니다.");
        }
        if (endAt != null && endAt.isBefore(startAt)) {
            throw new IllegalArgumentException("종료 시각은 시작 시각 이후여야 합니다.");
        }

        // MembershipSubscription 엔티티 생성
        MembershipSubscription subscription = new MembershipSubscription();
        subscription.user = user;
        subscription.membershipPlan = membershipPlan;
        subscription.status = MembershipSubscriptionStatus.ACTIVE;
        subscription.startAt = startAt;
        subscription.endAt = endAt;
        subscription.autoRenew = true; // 기본값
        subscription.cancelAtPeriodEnd = false; // 기본값
        subscription.retryCount = 0; // 기본값
        subscription.maxRetry = 3; // 기본값

        return subscription;
    }
}


