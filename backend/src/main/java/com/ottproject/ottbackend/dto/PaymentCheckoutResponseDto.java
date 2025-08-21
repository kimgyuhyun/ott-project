package com.ottproject.ottbackend.dto;

/**
 * 결제 체크아웃 생성 응답 DTO
 *
 * 큰 흐름
 * - 체크아웃 세션 생성 후 프론트에서 이동할 URL과 내부 결제 ID를 제공한다.
 *
 * 필드 개요
 * - redirectUrl/paymentId
 */
public class PaymentCheckoutResponseDto {
    public String redirectUrl; // 결제창으로 이동할 리다이렉트 URL
    public Long paymentId; // 내부 결제 레코드 ID
}
