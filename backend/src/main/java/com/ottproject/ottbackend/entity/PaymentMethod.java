package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.PaymentMethodType;
import com.ottproject.ottbackend.enums.PaymentProvider;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
     * FK 바인딩 전용 참조 — id 만 채운 비영속 인스턴스를 만든다.
     * Payment 의 payment_method_id 를 채우려고 결제수단 행 전체를 읽어오는 것을 피하는 자리에만 쓴다.
     * 나머지 필드는 비어 있으므로 이 인스턴스를 읽거나 저장 대상으로 삼지 않는다.
     *
     * @param id 결제수단 PK
     */
    public static PaymentMethod reference(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("결제수단 ID는 필수입니다.");
        }
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.id = id;
        return paymentMethod;
    }

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
    public static PaymentMethod createPaymentMethod(
            User user, PaymentProvider provider, PaymentMethodType type, String providerMethodId) {
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

    // ===== 등록 시 부가 정보 =====

    /**
     * 카드 표기 정보 부착 — 브랜드·끝 4자리·만료월·만료연도는 한 벌이다.
     * - 만료월과 만료연도를 따로 바꾸면 "13월"이나 "지난 해 12월" 같은 반쪽 만료일이 남는다.
     */
    public void describeCard(String brand, String last4, Integer expiryMonth, Integer expiryYear) {
        this.brand = brand;
        this.last4 = last4;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
    }

    /** 목록 노출 정책 지정 — 기본 수단 여부와 폴백 우선순위와 별칭. */
    public void applyListingOptions(boolean isDefault, int priority, String label) {
        this.isDefault = isDefault;
        this.priority = priority;
        this.label = label;
    }

    // ===== 수정 =====

    /** 별칭 변경 */
    public void rename(String label) {
        this.label = label;
    }

    /** 폴백 우선순위 변경(낮을수록 우선) */
    public void changePriority(int priority) {
        this.priority = priority;
    }

    /** 만료일 변경 — 월과 연도는 함께 바뀐다(describeCard 와 같은 이유). */
    public void changeExpiry(Integer expiryMonth, Integer expiryYear) {
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
    }

    // ===== 기본 수단 지정 =====
    // "사용자당 기본 수단은 하나"는 여러 행에 걸친 규칙이라 Service 가 지킨다(PaymentMethodService.setDefault).
    // 엔티티는 자기 행의 플래그만 책임진다.

    /** 기본 수단으로 지정 */
    public void markAsDefault() {
        this.isDefault = true;
    }

    /** 기본 수단 해제 */
    public void clearDefault() {
        this.isDefault = false;
    }

    // ===== 삭제 =====

    /**
     * 소프트 삭제 — 삭제 시각 기록과 기본 수단 해제를 함께 한다.
     * - 삭제된 행에 isDefault 가 남아 있으면 사용자에게 기본 수단이 둘로 보이는 데이터가 만들어진다.
     *   호출자는 재지정이 필요한지 판단하려고 삭제 전에 isDefault 를 읽어야 한다.
     * @param deletedAt 삭제 시각
     */
    public void softDelete(LocalDateTime deletedAt) {
        if (deletedAt == null) {
            throw new IllegalArgumentException("삭제 시각은 필수입니다.");
        }
        this.deletedAt = deletedAt;
        this.isDefault = false;
    }

    // ===== 게이트웨이가 확인해 준 실제 수단 =====

    /**
     * 결제 성공 후 게이트웨이가 알려준 실제 결제수단 확정.
     * - 제공자·유형·브랜드는 한 벌이다. 유형만 간편결제로 바꾸고 카드 브랜드를 남기면
     *   "카카오페이 VISA" 같은 표기가 사용자에게 그대로 나간다.
     */
    public void applyGatewayMethodDetails(PaymentProvider provider, PaymentMethodType type, String brand) {
        if (provider == null) {
            throw new IllegalArgumentException("결제 제공자는 필수입니다.");
        }
        if (type == null) {
            throw new IllegalArgumentException("결제수단 타입은 필수입니다.");
        }
        this.provider = provider;
        this.type = type;
        this.brand = brand;
    }
}
