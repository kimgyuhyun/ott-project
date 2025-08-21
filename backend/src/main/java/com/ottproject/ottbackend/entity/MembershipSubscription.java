package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
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
 */
@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscription_user", columnList = "user_id") // 사용자 인덱스
})
@Getter
@Setter
@Builder
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

    @Column(nullable = false)
    private LocalDateTime startAt; // 시작 시각

    @Column(nullable = true)
    private LocalDateTime endAt; // 종료 시각(null=무기한)

    @Column(nullable = false)
    private boolean autoRenew; // 자동 갱신 여부

    @Column(nullable = false)
    private boolean cancelAtPeriodEnd; // 말일 해지 예약 여부

    @Column(nullable = true) // 해지 확정 시각 기록
    private LocalDateTime canceledAt; // 해지 확정 시각(상태 CANCELED 전환 시점)

    @Column(nullable = true)
    private LocalDateTime nextBillingAt; // 다음 결제(갱신) 기준 시점

    // ===== 정기결제 재시도(dunning) 정책 필드 =====
    @Column(nullable = false)
    private int retryCount; // 현재까지 재시도 횟수(소프트 디클라인 기준)

    @Column(nullable = false)
    private int maxRetry; // 최대 재시도 횟수(기본 3)

    @Column(nullable = true)
    private LocalDateTime lastRetryAt; // 마지막 재시도 시각

    @Column(length = 100)
    private String lastErrorCode; // 마지막 실패 코드(게이트웨이)

    @Column(length = 500)
    private String lastErrorMessage; // 마지막 실패 메시지(게이트웨이)
}


