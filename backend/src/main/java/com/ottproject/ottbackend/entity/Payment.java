package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.entity.PaymentMethod;
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
@Setter
@Builder
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 모든 필드 생성자
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
    @Builder.Default // 빌더 사용 시 기본값 설정
    private PaymentStatus status = PaymentStatus.PENDING; // 초기 상태 PENDING
    
    @Column(length = 255)
    private String providerSessionId; // 체크아웃 세션 식별자
    
    @Column(length = 255)
    private String providerPaymentId; // 최종 결제 식별자
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod; // 사용된 결제수단
    
    @Column(length = 2048)
    private String receiptUrl; // 영수증 URL(성공 시)
    
    @Column
    private LocalDateTime paidAt; // 성공 시각
    
    @Column
    private LocalDateTime failedAt; // 실패 시각
    
    @Column LocalDateTime canceledAt; // 취소 시각
    
    @Column
    private Long refundedAmount; // 환불 금액(최소 화폐단위)
    
    @Column
    private LocalDateTime refundedAt; // 환불 완료 시각
    
    @CreatedDate // 생성 시각 자동 기록
    @Column(nullable = false)
    private LocalDateTime createdAt; // 생성 시각
    
    @LastModifiedDate // 수정 시각 자동 기록
    @Column(nullable = false)
    private LocalDateTime updatedAt; // 수정 시각
}
