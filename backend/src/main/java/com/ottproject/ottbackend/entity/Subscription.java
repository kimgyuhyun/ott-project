package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 사용자 구독 엔티티
 * - 구독 플랜/상태/기간 관리
 */
@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscription_user", columnList = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription { // 사용자 구독
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 소유자

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan; // 플랜

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.ottproject.ottbackend.enums.SubscriptionStatus status; // 상태

    @Column(nullable = false)
    private java.time.LocalDateTime startAt; // 시작 시각

    @Column(nullable = true)
    private java.time.LocalDateTime endAt; // 종료 시각(null=무기한)
}


