package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Payment 엔티티
 *
 * 큰 흐름
 * - 멤버십 결제의 단건 수명주기를 저장(PENDING→SUCCEEDED/FAILED/CANCELED/REFUNDED).
 * - 게이트웨이 식별자(세션/결제)와 영수증 URL 등 외부 메타를 함께 보관한다.
 * - 가격은 `Money` VO 로 임베드되어 금액/통화 일관성을 제공한다.
 *
 * 필드 개요
 * - id: PK
 * - user/membershipPlan: 결제 주체/대상
 * - provider/providerSessionId/providerPaymentId: 게이트웨이/외부 식별자
 * - price: 금액/통화 VO
 * - status: 결제 상태
 * - receiptUrl/paidAt/failedAt/canceledAt/refundedAt/refundedAmount: 상태 메타데이터
 * - createdAt/updatedAt: 생성/수정 시각(Auditing)
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor // 기본 생성자
@EntityListeners(AuditingEntityListener.class) // 생성/수정 일시 자동 기록(Auditing)
public class Payment { // 엔티티 시작
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 결제 레코드 PK
    
    @ManyToOne(fetch = FetchType.LAZY) // 사용자와 다대일 연관(지연 로딩)
    @JoinColumn(name = "user_id", nullable = false) // FK: user.id
    private User user; // 결제한 사용자
    
    @ManyToOne(fetch = FetchType.LAZY) // 플랜과 다대일 연관(지연 로딩)
    @JoinColumn(name = "plan_id", nullable = false) // FK: membership_plans.id
    private MembershipPlan membershipPlan; // 결제 대상 플랜
    
    @Enumerated(EnumType.STRING) // enum 의 문자열 값으로 저장
    @Column(nullable = false)
    private PaymentProvider provider; // 결제 제공자(STRIPE/IMPORT 등)
    
    @Embedded // Money VO 임베드
    private Money price; // 결제 금액/통화 VO
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING; // 초기 상태 PENDING
    
    // 유니크: 정기결제 재청구가 PG 호출 전에 결정적 merchant_uid 로 PENDING 을 선삽입하고,
    // 중복 배달의 두 번째 삽입이 여기서 떨어지는 것이 이중 청구를 막는 1차 방어선이다.
    // NULL 은 서로 충돌하지 않으므로 값이 없는 행에는 영향이 없다.
    // 운영 스키마는 V20260729100000 마이그레이션이 만든다(여기 선언은 매핑 일치 + 테스트 스키마용).
    @Column(length = 255, unique = true)
    private String providerSessionId; // 체크아웃 세션 식별자(merchant_uid)
    
    @Column(length = 255)
    private String providerPaymentId; // 최종 결제 식별자
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod; // 사용된 결제수단
    
    @Column(length = 2048)
    private String receiptUrl; // 영수증 URL(성공 시)
    
    @Column(length = 2048)
    private String description; // 결제 설명
    
    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON 형태의 메타데이터
    
    @Column
    private LocalDateTime paidAt; // 성공 시각
    
    @Column
    private LocalDateTime failedAt; // 실패 시각
    
    @Column
    private LocalDateTime canceledAt; // 취소 시각
    
    @Column
    private Long refundedAmount; // 환불 금액(최소 화폐단위)
    
    @Column
    private LocalDateTime refundedAt; // 환불 완료 시각
    
    @Column
    private LocalDateTime completedAt; // 결제 완료 시각
    
    @CreatedDate // 생성 시각 자동 기록
    @Column(nullable = false)
    private LocalDateTime createdAt; // 생성 시각
    
    @LastModifiedDate // 수정 시각 자동 기록
    @Column(nullable = false)
    private LocalDateTime updatedAt; // 수정 시각

    // ===== 정적 팩토리 메서드 =====

    /**
     * 대기 중인 결제 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 결제 사용자
     * @param membershipPlan 결제 대상 플랜
     * @param provider 결제 제공자
     * @param sessionId 세션 ID
     * @param price 결제 금액
     * @return 생성된 Payment 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static Payment createPendingPayment(User user, MembershipPlan membershipPlan, 
                                              PaymentProvider provider, String sessionId, Money price) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (membershipPlan == null) {
            throw new IllegalArgumentException("멤버십 플랜은 필수입니다.");
        }
        if (provider == null) {
            throw new IllegalArgumentException("결제 제공자는 필수입니다.");
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("세션 ID는 필수입니다.");
        }
        if (price == null || price.getAmount() <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }

        // Payment 엔티티 생성
        Payment payment = new Payment();
        payment.user = user;
        payment.membershipPlan = membershipPlan;
        payment.provider = provider;
        payment.providerSessionId = sessionId.trim();
        payment.price = price;
        payment.status = PaymentStatus.PENDING;

        return payment;
    }

    /**
     * 성공한 결제 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 결제 사용자
     * @param membershipPlan 결제 대상 플랜
     * @param provider 결제 제공자
     * @param paymentId 결제 ID
     * @param price 결제 금액
     * @param paidAt 결제 완료 시각
     * @return 생성된 Payment 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static Payment createSucceededPayment(User user, MembershipPlan membershipPlan, 
                                                PaymentProvider provider, String paymentId, 
                                                Money price, LocalDateTime paidAt) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (membershipPlan == null) {
            throw new IllegalArgumentException("멤버십 플랜은 필수입니다.");
        }
        if (provider == null) {
            throw new IllegalArgumentException("결제 제공자는 필수입니다.");
        }
        if (paymentId == null || paymentId.trim().isEmpty()) {
            throw new IllegalArgumentException("결제 ID는 필수입니다.");
        }
        if (price == null || price.getAmount() <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }
        if (paidAt == null) {
            throw new IllegalArgumentException("결제 완료 시각은 필수입니다.");
        }

        // Payment 엔티티 생성
        Payment payment = new Payment();
        payment.user = user;
        payment.membershipPlan = membershipPlan;
        payment.provider = provider;
        payment.providerPaymentId = paymentId.trim();
        payment.price = price;
        payment.status = PaymentStatus.SUCCEEDED;
        payment.paidAt = paidAt;
        payment.completedAt = paidAt;

        return payment;
    }

    /**
     * 실패한 결제 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 결제 사용자
     * @param membershipPlan 결제 대상 플랜
     * @param provider 결제 제공자
     * @param sessionId 세션 ID
     * @param price 결제 금액
     * @param failedAt 실패 시각
     * @return 생성된 Payment 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static Payment createFailedPayment(User user, MembershipPlan membershipPlan, 
                                             PaymentProvider provider, String sessionId, 
                                             Money price, LocalDateTime failedAt) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (membershipPlan == null) {
            throw new IllegalArgumentException("멤버십 플랜은 필수입니다.");
        }
        if (provider == null) {
            throw new IllegalArgumentException("결제 제공자는 필수입니다.");
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("세션 ID는 필수입니다.");
        }
        if (price == null || price.getAmount() <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }
        if (failedAt == null) {
            throw new IllegalArgumentException("실패 시각은 필수입니다.");
        }

        // Payment 엔티티 생성
        Payment payment = new Payment();
        payment.user = user;
        payment.membershipPlan = membershipPlan;
        payment.provider = provider;
        payment.providerSessionId = sessionId.trim();
        payment.price = price;
        payment.status = PaymentStatus.FAILED;
        payment.failedAt = failedAt;

        return payment;
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 결제 상태를 성공으로 변경
     * @param paymentId 결제 ID
     * @param paidAt 결제 완료 시각
     * @throws IllegalStateException 현재 상태에서 변경할 수 없는 경우
     */
    public void markAsSucceeded(String paymentId, LocalDateTime paidAt) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("대기 중인 결제만 성공으로 변경할 수 있습니다.");
        }
        if (paymentId == null || paymentId.trim().isEmpty()) {
            throw new IllegalArgumentException("결제 ID는 필수입니다.");
        }
        if (paidAt == null) {
            throw new IllegalArgumentException("결제 완료 시각은 필수입니다.");
        }

        this.status = PaymentStatus.SUCCEEDED;
        this.providerPaymentId = paymentId.trim();
        this.paidAt = paidAt;
        this.completedAt = paidAt;
    }

    /**
     * 결제 상태를 실패로 변경
     * @param failedAt 실패 시각
     * @throws IllegalStateException 현재 상태에서 변경할 수 없는 경우
     */
    public void markAsFailed(LocalDateTime failedAt) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("대기 중인 결제만 실패로 변경할 수 있습니다.");
        }
        if (failedAt == null) {
            throw new IllegalArgumentException("실패 시각은 필수입니다.");
        }

        this.status = PaymentStatus.FAILED;
        this.failedAt = failedAt;
    }

    /**
     * 환불 처리
     * @param refundedAmount 환불 금액
     * @param refundedAt 환불 시각
     * @throws IllegalStateException 환불할 수 없는 상태인 경우
     * @throws IllegalArgumentException 환불 금액이 유효하지 않은 경우
     */
    public void processRefund(Long refundedAmount, LocalDateTime refundedAt) {
        if (this.status != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("성공한 결제만 환불할 수 있습니다.");
        }
        if (refundedAmount == null || refundedAmount <= 0) {
            throw new IllegalArgumentException("환불 금액은 0보다 커야 합니다.");
        }
        if (refundedAmount > this.price.getAmount()) {
            throw new IllegalArgumentException("환불 금액은 결제 금액을 초과할 수 없습니다.");
        }
        if (refundedAt == null) {
            throw new IllegalArgumentException("환불 시각은 필수입니다.");
        }

        this.refundedAmount = refundedAmount;
        this.refundedAt = refundedAt;
        this.status = PaymentStatus.REFUNDED;
    }

    // ===== 게이트웨이가 관측한 결과 반영 =====
    // 위쪽 markAs*/processRefund 는 우리가 주도하는 전이라 PENDING 등 사전 상태를 요구한다.
    // 아래 apply* 는 웹훅과 대사가 "이미 일어난 일"을 뒤늦게 반영하는 자리라 사전 상태를 요구하지 않는다.
    // 순서가 뒤바뀌거나 늦게 도착한 이벤트에 예외를 던지면 웹훅이 200 을 못 돌려주고 재전송만 반복된다.

    /**
     * 게이트웨이 성공 반영
     * - 상태·결제시각·완료시각·외부 결제 ID 를 한 번에 확정한다.
     * - 영수증 URL 은 있을 때만 갱신한다(클라이언트 확정 경로는 null 로 들어온다).
     */
    public void applyGatewaySuccess(String providerPaymentId, String receiptUrl, LocalDateTime paidAt) {
        if (paidAt == null) {
            throw new IllegalArgumentException("결제 완료 시각은 필수입니다.");
        }
        this.status = PaymentStatus.SUCCEEDED;
        this.providerPaymentId = providerPaymentId;
        this.paidAt = paidAt;
        this.completedAt = paidAt;
        if (receiptUrl != null) {
            this.receiptUrl = receiptUrl;
        }
    }

    /**
     * 게이트웨이 실패 반영 — 상태와 실패 시각은 반드시 함께 바뀐다.
     */
    public void applyGatewayFailure(LocalDateTime failedAt) {
        if (failedAt == null) {
            throw new IllegalArgumentException("실패 시각은 필수입니다.");
        }
        this.status = PaymentStatus.FAILED;
        this.failedAt = failedAt;
    }

    /**
     * 게이트웨이 취소 반영 — 상태와 취소 시각은 반드시 함께 바뀐다.
     */
    public void applyGatewayCancellation(LocalDateTime canceledAt) {
        if (canceledAt == null) {
            throw new IllegalArgumentException("취소 시각은 필수입니다.");
        }
        this.status = PaymentStatus.CANCELED;
        this.canceledAt = canceledAt;
    }

    /**
     * 게이트웨이 환불 반영 — 상태와 환불 시각과 환불 금액은 반드시 함께 바뀐다.
     */
    public void applyGatewayRefund(Long refundedAmount, LocalDateTime refundedAt) {
        if (refundedAt == null) {
            throw new IllegalArgumentException("환불 시각은 필수입니다.");
        }
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = refundedAt;
        this.refundedAmount = refundedAmount;
    }

    // ===== 생성 직후 부가 정보 부착 =====

    /** 결제수단 연결 */
    public void attachPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    /** 결제 설명 부여 */
    public void describeAs(String description) {
        this.description = description;
    }

    /** 메타데이터(JSON) 부착 */
    public void attachMetadata(String metadata) {
        this.metadata = metadata;
    }

    /** 영수증 URL 부착 — 게이트웨이가 성공 확정 뒤에 따로 내려주는 경우가 있다. */
    public void attachReceipt(String receiptUrl) {
        this.receiptUrl = receiptUrl;
    }
}
