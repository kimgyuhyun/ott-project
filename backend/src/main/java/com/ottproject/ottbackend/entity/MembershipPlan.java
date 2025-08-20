package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 구독 플랜 엔티티
 * - 코드/표기명/허용 최대 화질
 */
@Entity
@Table(name = "plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MembershipPlan { // 멤버쉽 플랜
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) // pk 자동 증가
    private Long id; // PK

    @Column(nullable = false, unique = true) // 고유 코드 제약
    private String code; // e.g., FREE, BASIC, PREMIUM

    @Column(nullable = false)
    private String name; // 표기명

    @Column(nullable = false)
    private String maxQuality; // "720p" / "1080p"

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "price_monthly_vat_included", nullable = false)),
        @AttributeOverride(name = "currency", column = @Column(name = "price_currency", length = 3, nullable = false))
    })
    private Money price; // 월 가격(VO: 금액/통화)

    @Column(nullable = false)
    private Integer periodMonths; // 청구주기 (월 단위, 1~월간, 12~연간 등)

    @Column(nullable = false)
    private Integer concurrentStreams; // 동시접속 허용 수
}


