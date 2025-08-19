package com.ottproject.ottbackend.dto;
/**
 * 체크아웃(결제창) 생성 요청 DTO
 *
 * 역할:
 * - 사용자가 특정 멤버십 플랜 결제 진행을 위해 결제창 생성을 요청할 때 사용
 * - 성공/취소 리다이렉트 URL은 선택이며 서버 기본값이 있을 수 있음
 * - 멱등키 전달 시 중복 체크아웃 생성을 방지
 */
public class PaymentCheckoutCreateRequestDto { // 체크아웃 생성 요청 DTO 클래스 시작
	public String planCode; // 결제 대상 멤버십 플랜 코드
	public String successUrl; // 결제 성공 시 리다이렉트 URL(선택)
	public String cancelUrl; // 결제 취소 시 리다이렉트 URL(선택)
	public String idempotencyKey; // 멱등키(선택, 중복 요청 방지)
}


