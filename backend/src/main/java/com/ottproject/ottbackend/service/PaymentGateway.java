package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.User;

/**
 * PaymentGateway
 *
 * 큰 흐름
 * - 외부 결제 게이트웨이(아임포트/Stripe 등) 연동을 표준화한다.
 *
 * 메서드 개요
 * - createCheckoutSession: 체크아웃 세션 생성
 * - issueRefund: 환불 처리
 * - chargeWithSavedMethod: 저장수단 자동 청구
 * - verifyWebhookBasicValidation: 웹훅 기본 검증
 */
public interface PaymentGateway { // 게이트웨이 추상화 시작

	/**
	 * 체크아웃 세션 생성 (prepare-only)
	 *
	 * 동작:
	 * - 게이트웨이에 /payments/prepare 등록만 수행하여 merchant_uid(세션)와 금액을 고정한다.
	 * - 실제 결제창 호출은 프론트엔드 JS SDK가 수행한다.
	 *
	 * 반환:
	 * - sessionId: 게이트웨이 세션 식별자(merchant_uid)
	 * - redirectUrl: (prepare-only에서는 사용 안 함, null 권장)
	 */
	CheckoutSession createCheckoutSession(User user, MembershipPlan plan, String successUrl, String cancelUrl, String paymentService, long amount); // 세션 생성 시그니처

	final class CheckoutSession { // 반환 DTO 내장형
		public String sessionId; // 게이트웨이 세션 ID
		public String redirectUrl; // 결제창 이동 URL
	}

	/**
	 * 환불 요청
	 * - 전액 또는 일부 환불 처리 후 결과 반환
	 */
	RefundResult issueRefund(String providerPaymentId, long amount); // 환불 실행 시그니처

	final class RefundResult { // 환불 결과 내장형
		public String providerRefundId; // 외부 환불 식별자
		public java.time.LocalDateTime refundedAt; // 환불 완료 시각
	}

	/**
	 * 저장된 결제수단(빌링키)로 자동 청구 수행
	 * - 성공 시 외부 결제 식별자/시각/영수증 URL 반환
	 */
	ChargeResult chargeWithSavedMethod(String providerCustomerId, String providerMethodId, long amount, String currency, String description);

	final class ChargeResult {
		public String providerPaymentId;
		public java.time.LocalDateTime paidAt;
		public String receiptUrl;
	}

	/**
	 * 웹훅 기본 검증
	 * - 게이트웨이가 보낸 웹훅 데이터의 기본 유효성을 검증합니다.
	 * - 실제 검증은 웹훅 처리 후 API 호출로 수행합니다.
	 */
	boolean verifyWebhookBasicValidation(String rawBody, java.util.Map<String, String> headers);

	/**
	 * 결제 실패 유형
	 * - HARD_DECLINE: 영구적 실패(재시도 불가)
	 * - SOFT_DECLINE: 일시적 실패(재시도 가능)
	 */
	enum FailureType { HARD_DECLINE, SOFT_DECLINE }

	/**
	 * 결제 예외(유형/코드/메시지 포함)
	 */
	final class ChargeException extends RuntimeException {
		public final FailureType failureType;
		public final String errorCode;
		public ChargeException(FailureType type, String code, String message) {
			super(message);
			this.failureType = type;
			this.errorCode = code;
		}
	}
}


