package com.ottproject.ottbackend.dto;


/**
 * 결제 체크아웃 생성 요청 DTO
 *
 * 큰 흐름
 * - 사용자가 특정 플랜 결제를 위해 체크아웃 생성을 요청한다.
 * - 성공/취소 URL은 선택이며 서버 기본값이 있을 수 있다.
 * - idempotencyKey 로 중복 생성을 방지한다.
 *
 * 필드 개요
 * - planCode/successUrl/cancelUrl/idempotencyKey
 */
public class PaymentCheckoutRequestDto {
    public String planCode; // 결제 대상 플랜 코드
    public String successUrl; // 결제 성공 시 리다이렉트 URL(선택)
    public String cancelUrl; // 결제 취소 시 리다이렉트 URL(선택)
    public String idempotencyKey; // 역등키(선택, 중복 요청 방지)

}
