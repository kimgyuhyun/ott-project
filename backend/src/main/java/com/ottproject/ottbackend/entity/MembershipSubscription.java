package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 사용자 구독 엔티티
 * - 구독 플랜/상태/기간 관리
 * - 자동갱신//일일해지/취소시각/다음결제 anchor
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
    private boolean cancelAtPeriodEnd; // 일일 해지 예약 여부

    @Column(nullable = true) // 즉시 해지 시점 기록
    private LocalDateTime canceledAt; // 취소 처리 시각

    @Column(nullable = true)
    private LocalDateTime nextBillingAt; // 다음 결제(갱신) 기준 시점
}


