package com.ottproject.ottbackend.dto;
/**
 * 체크아웃(결제창) 생성 요청 DTO
 *
 * 큰 흐름
 * - 사용자가 특정 멤버십 플랜 결제를 위해 결제창 생성을 요청한다.
 * - 성공/취소 리다이렉트 URL은 선택이며 서버 기본값이 있을 수 있다.
 * - idempotencyKey 로 중복 생성을 방지한다.
 *
 * 필드 개요
 * - planCode/successUrl/cancelUrl/idempotencyKey
 */
public class PaymentCheckoutCreateRequestDto { // 체크아웃 생성 요청 DTO 클래스 시작
    public String planCode; // 결제 대상 멤버십 플랜 코드
    public String successUrl; // 결제 성공 시 리다이렉트 URL(선택)
    public String cancelUrl; // 결제 취소 시 리다이렉트 URL(선택)
    public String idempotencyKey; // 멱등키(필수, UUID). 같은 키의 재요청에는 첫 응답을 돌려준다(#191)
    public String paymentService; // 결제 서비스 선택(kakao|toss|nice)
}
