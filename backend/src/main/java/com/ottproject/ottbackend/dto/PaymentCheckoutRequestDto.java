package com.ottproject.ottbackend.dto;


/**
 * 결제 체크아웃 생성 요청 DTO
 *
 * 역할:
 * - 사용자가 특정 플랜을 선택해 결제창(체크아웃) 생성을 요청할 때 사용합니다.
 * - 성공/취소 URL 은 선택이며, 서버 기본값이 있을 경우 null 이어도 동작합니다.
 * - 멱등 요청 시 idempotencyKey 를 함께 전달하면 중복 생성을 방지할 수 있습니다.
 *
 * 주요 필드:
 * - planCode: 대상 멤버십 플랜 코드
 * - successUrl/cancelUrl: 결제 완료 또는 취소 시 리다이렉션 URL
 * - idempotencyKey: 멱등 키(선택)
 */
public class PaymentCheckoutRequestDto {
    public String planCode; // 결제 대상 플랜 코드
    public String successUrl; // 결제 성공 시 리다이렉트 URL(선택)
    public String cancelUrl; // 결제 취소 시 리다이렉트 URL(선택)
    public String idempotencyKey; // 역등키(선택, 중복 요청 방지)

}
