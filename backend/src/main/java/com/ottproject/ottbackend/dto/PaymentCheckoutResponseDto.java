package com.ottproject.ottbackend.dto;

/**
 * 결제 체크아웃 생성 응답 DTO
 *
 * 역할:
 * - 체크아웃 세션 생성 후 프론트엔드에서 이동할 리다이렉트 URL 을 제공합니다.
 * - 서버 내부 결제 레코드 식별자(paymentId)를 함께 반환해 상태 추적에 사용합니다.
 *
 * 주요 필드:
 * - redirectUrl: 외부 결제창(체크아웃)으로 이동할 URL
 * - paymentId: 내부 결제 레코드 식별자
 */
public class PaymentCheckoutResponseDto {
    public String redirectUrl; // 결제창으로 이동할 리다이렉트 URL
    public Long paymentId; // 내부 결제 레코드 ID
}
