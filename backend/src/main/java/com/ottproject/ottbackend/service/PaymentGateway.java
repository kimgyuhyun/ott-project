package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.User;

/**
 * PaymentGateway
 *
 * 역할:
 * - IMPORT(아임포트) 등 결제 게이트웨이 연동을 추상화하는 인터페이스
 * - 체크아웃 세션 생성과 같은 외부 호출을 캡슐화
 */
public interface PaymentGateway { // 게이트웨이 추상화 시작

	/**
 	 * 체크아웃 세션 생성
 	 *
 	 * 반환:
 	 * - sessionId: 게이트웨이 세션 식별자
 	 * - redirectUrl: 결제창으로 이동할 URL
 	 */
	CheckoutSession createCheckoutSession(User user, MembershipPlan plan, String successUrl, String cancelUrl); // 세션 생성 시그니처

	final class CheckoutSession { // 반환 DTO 내장형
		public String sessionId; // 게이트웨이 세션 ID
		public String redirectUrl; // 결제창 이동 URL
	}

	/**
	 * 환불 요청
	 * - 전액 환불(또는 필요 시 일부 환불) 처리 후 결과 반환
	 */
	RefundResult issueRefund(String providerPaymentId, long amount); // 환불 실행 시그니처

	final class RefundResult { // 환불 결과 내장형
		public String providerRefundId; // 외부 환불 식별자
		public java.time.LocalDateTime refundedAt; // 환불 완료 시각
	}
}


