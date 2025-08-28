package com.ottproject.ottbackend.dto;

/**
 * 체크아웃(결제창) 생성 성공 응답 DTO
 *
 * 큰 흐름
 * - 결제창으로 이동할 URL과 내부 결제 ID를 반환한다.
 *
 * 필드 개요
 * - redirectUrl/paymentId
 */
public class PaymentCheckoutCreateSuccessResponseDto { // 체크아웃 생성 성공 응답 DTO 클래스 시작
	public String redirectUrl; // 결제창 리다이렉트 URL
	public Long paymentId; // 내부 결제 레코드 ID(상태 추적용)
    public String providerSessionId; // 게이트웨이 세션/merchant_uid
    public Long amount; // 결제 금액(검증용)
}


