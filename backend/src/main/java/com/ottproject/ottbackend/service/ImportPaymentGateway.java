package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.User;
import org.springframework.stereotype.Component;

/**
 * ImportPaymentGateway
 *
 * 역할:
 * - IMPORT(아임포트) 연동 구현체
 * - 실제로는 REST API 호출을 수행하여 결제창 세션을 생성해야 함
 * - 여기서는 정상동작 기준의 골격을 제공(실제 API 연동부는 TODO)
 */
@Component // 스프링 컴포넌트 등록
public class ImportPaymentGateway implements PaymentGateway { // IMPORT 구현 시작
	@Override // 인터페이스 구현
	public CheckoutSession createCheckoutSession(User user, MembershipPlan plan, String successUrl, String cancelUrl) { // 세션 생성
		// TODO: iamport REST API 호출로 결제창 세션 생성 및 redirectUrl 수신
		CheckoutSession session = new CheckoutSession(); // 반환 객체 생성
		session.sessionId = "imp_session_" + System.currentTimeMillis(); // 세션ID(예시)
		session.redirectUrl = "https://payment.import.com/checkout/" + session.sessionId; // 리다이렉트 URL(예시)
		return session; // 반환
	}

	@Override // 인터페이스 구현
	public RefundResult issueRefund(String providerPaymentId, long amount) { // 환불 실행
		// TODO: iamport 환불 REST API 호출하여 providerRefundId, refundedAt 수신
		RefundResult result = new RefundResult(); // 결과 객체 생성
		result.providerRefundId = "imp_refund_" + System.currentTimeMillis(); // 예시 환불 ID
		result.refundedAt = java.time.LocalDateTime.now(); // 현재 시각을 환불 완료 시각으로 사용(예시)
		return result; // 반환
	}
}


