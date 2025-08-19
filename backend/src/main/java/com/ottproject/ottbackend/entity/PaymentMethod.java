package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.PaymentMethodType;
import com.ottproject.ottbackend.enums.PaymentProvider;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * PaymentMethod 엔티티
 *
 * 사용자가 등록한 결제 수단을 표현합니다.
 * - 기본 결제 수단과 보조(백업) 결제 수단을 함께 관리합니다.
 * - 결제 실패 시 우선순위(priority)가 낮은 수단으로 폴백할 수 있도록 설계합니다.
 * - 결제 제공자(예: STRIPE/IMPORT)의 결제수단 식별자/토큰을 안전하게 저장합니다.
 *
 * 필드 개요:
 * - user: 소유 사용자
 * - provider, type, providerMethodId: 결제 제공자와 결제수단의 외부 식별 정보
 * - brand, last4, expiryMonth, expiryYear: 카드 정보 마스킹/만료 정보
 * - isDefault, priority, label: 기본 수단 여부/우선순위/별칭
 * - deletedAt: 소프트 삭제 시각(해지)
 * - createdAt/updatedAt: 감사용 생성/수정 시각
 */
@Entity
@Table(name = "payment_methods")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class) // 생성 수정 일시 자동 기록
public class PaymentMethod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 결제수단 레코드 PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 소유하고있는 사용자

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider; // 결제 제공자(STRIPE/IMPORT 등)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethodType type; // 결제 수단 타입(CARD, ACCOUNT 등)

    @Column(nullable = false, length = 255)
    private String providerMethodId; // 제공자 측 결제수단 식별자(토큰/키)

    @Column(length = 50)
    private String brand; // 카드 브랜드(예: VISA, Master)

    @Column(length = 4)
    private String last4; // 카드 번호 마지막 4자리(마스킹)

    @Column
    private Integer expiryMonth; // 만료 월(카드)

    @Column
    private Integer expiryYear; // 만료 연도(카드)

    @Column(nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @Column(nullable = false)
    @Builder.Default
    private int priority = 100;

    @Column(length = 100)
    private String label;

    @Column
    private LocalDateTime deletedAt;

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

}
