package com.ottproject.ottbackend.dto;

/**
 * 체크아웃(결제창) 생성 성공 응답 DTO
 *
 * 역할:
 * - 결제창으로 이동할 리다이렉트 URL과 내부 결제 ID를 반환
 * - 프론트엔드는 redirectUrl로 이동해 결제를 진행
 */
public class PaymentCheckoutCreateSuccessResponseDto { // 체크아웃 생성 성공 응답 DTO 클래스 시작
	public String redirectUrl; // 결제창 리다이렉트 URL
	public Long paymentId; // 내부 결제 레코드 ID(상태 추적용)
}


