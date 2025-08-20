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
 * 결제(청구) 단건을 표현하는 도메인 모델입니다.
 * - 멤버십 플랜에 대한 결제 시도/성공/실패/취소/환불 상태를 저장합니다.
 * - 결제 제공자(Stripe/Import 등)의 세션/결제 식별자와 영수증 URL 을 보관합니다.
 * - 성공(SUCCEEDED) 시점에 멤버십 구독 활성/연장 로직이 트리거됩니다(서비스 계층).
 *
 * 필드 개요:
 * - user, membershipPlan: 누가 어떤 플랜을 결제했는지
 * - provider, providerSessionId, providerPaymentId: 결제 제공자와 외부 식별자
 * - amount, currency: 결제 금액과 통화(최소 화폐단위 기준, VAT 포함 월 기준 보관)
 * - status: 결제 상태(PENDING → SUCCEEDED/FAILED/CANCELED/REFUNDED)
 * - paidAt/failedAt/canceledAt/refundedAt, refundedAmount, receiptUrl: 상태 변화의 메타데이터
 * - createdAt/updatedAt: 감사를 위한 생성/수정 시각
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@Builder
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 모든 필드 생성자
@EntityListeners(AuditingEntityListener.class) // 생성/수정 일시 자동 기록(Auditing)
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 결제 레코드 pk
    
    @ManyToOne(fetch = FetchType.LAZY) // 사용자와 다대일 연관(지연 로딩)
    @JoinColumn(name = "user_id", nullable = false) // FK: user.id
    private User user; // 결제를 수정한 사용자
    
    @ManyToOne(fetch = FetchType.LAZY) // 플랜과 다대일 연관(지연 로딩)
    @JoinColumn(name = "plan_id", nullable = false) // FK: membership_plans.id
    private MembershipPlan membershipPlan; // 결제 대상 멤버십 플랜
    
    @Enumerated(EnumType.STRING) // enum 의 문자열 값으로 저장
    @Column(nullable = false)
    private PaymentProvider provider; // 결제 제공자(STRIPE/IMPORT 등)
    
    @Embedded // 금액/통화 VO 임베드
    private Money price; // 결제 금액 및 통화(VO)
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default // 빌더 사용 시 기본값 설정
    private PaymentStatus status = PaymentStatus.PENDING; // 결제 상태(초기 PENDING)
    
    @Column(length = 255)
    private String providerSessionId; // 결제창/세션 식별자(체크아웃 단계 식별)
    
    @Column(length = 255)
    private String providerPaymentId; // 최종 결제 식별자(승인 완료 후 제공)
    
    @Column(length = 2048)
    private String receiptUrl; // 영수증 URL(성공 시 제공)
    
    @Column
    private LocalDateTime paidAt; // 결제 성공 시각
    
    @Column
    private LocalDateTime failedAt; // 결제 실패 시각
    
    @Column LocalDateTime canceledAt; // 결제 취소 시각
    
    @Column
    private Long refundedAmount; // 환불 금액(최소 화폐단위)
    
    @Column
    private LocalDateTime refundedAt; // 환불 완료 시각
    
    @CreatedDate // 생성 시각 자동 기록
    @Column(nullable = false)
    private LocalDateTime createdAt; // 레코드 생성 시각
    
    @LastModifiedDate // 수정 시각 자동 기록
    @Column(nullable = false)
    private LocalDateTime updatedAt; // 레코드 수정 시각
}
