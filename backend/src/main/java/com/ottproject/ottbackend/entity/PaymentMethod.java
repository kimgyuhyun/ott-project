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
 * 큰 흐름
 * - 사용자의 저장 결제수단(기본/보조)을 관리한다.
 * - 우선순위(priority)와 기본 플래그로 폴백 전략을 지원한다.
 * - 게이트웨이의 결제수단 식별자/토큰을 안전 저장한다.
 *
 * 필드 개요
 * - id: PK
 * - user: 소유 사용자
 * - provider/type/providerMethodId: 게이트웨이/유형/외부 식별자
 * - brand/last4/expiryMonth/expiryYear: 카드 마스킹/만료 정보
 * - isDefault/priority/label: 기본 여부/우선순위/별칭
 * - deletedAt: 소프트 삭제 시각
 * - createdAt/updatedAt: 생성/수정 시각(Auditing)
 */
@Entity
@Table(name = "payment_methods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class) // 생성 수정 일시 자동 기록
public class PaymentMethod { // 엔티티 시작
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 결제수단 레코드 PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 소유 사용자

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider; // 결제 제공자(STRIPE/IMPORT 등)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethodType type; // 결제 수단 타입(CARD, KAKAO_PAY, TOSS_PAY, NICE_PAY 등)

    @Column(nullable = false, length = 255)
    private String providerMethodId; // 제공자 측 결제수단 식별자(토큰/키)

    @Column(length = 50)
    private String brand; // 결제 브랜드: 카드일 때만 사용(VISA/MasterCard/...) - 간편결제는 type으로 구분

    @Column(length = 4)
    private String last4; // 카드 번호 마지막 4자리(마스킹)

    @Column
    private Integer expiryMonth; // 만료 월(카드)

    @Column
    private Integer expiryYear; // 만료 연도(카드)

    @Column(nullable = false)
    private boolean isDefault = false; // 기본 수단 여부

    @Column(nullable = false)
    private int priority = 100; // 낮을수록 우선

    @Column(length = 100)
    private String label; // 별칭

    @Column
    private LocalDateTime deletedAt; // 소프트 삭제 시각

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt; // 생성 시각

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt; // 수정 시각

    // ===== 정적 팩토리 메서드 =====

    /**
     * 결제수단 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 사용자
     * @param provider 결제 제공자
     * @param type 결제수단 타입
     * @param providerMethodId 제공자별 결제수단 ID
     * @return 생성된 PaymentMethod 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static PaymentMethod createPaymentMethod(User user, PaymentProvider provider, 
                                                   PaymentMethodType type, String providerMethodId) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (provider == null) {
            throw new IllegalArgumentException("결제 제공자는 필수입니다.");
        }
        if (type == null) {
            throw new IllegalArgumentException("결제수단 타입은 필수입니다.");
        }
        if (providerMethodId == null || providerMethodId.trim().isEmpty()) {
            throw new IllegalArgumentException("제공자별 결제수단 ID는 필수입니다.");
        }

        // PaymentMethod 엔티티 생성
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.user = user;
        paymentMethod.provider = provider;
        paymentMethod.type = type;
        paymentMethod.providerMethodId = providerMethodId.trim();
        paymentMethod.isDefault = false; // 기본값
        paymentMethod.priority = 100; // 기본값

        return paymentMethod;
    }
}
