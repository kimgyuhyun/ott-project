package com.ottproject.ottbackend.dto; // DTO 패키지 선언

/**
 * 체크아웃(결제창) 생성 실패 응답 DTO
 *
 * 큰 흐름
 * - 체크아웃 생성 시 발생한 동기 오류를 코드/메시지/상세로 전달한다.
 *
 * 필드 개요
 * - code/message/detail: 오류 표준 정보
 */
public class PaymentCheckoutCreateErrorResponseDto { // 체크아웃 생성 실패 응답 DTO 클래스 시작
	public String code; // 표준 오류 코드(예: CHECKOUT_CREATE_FAILED, PLAN_NOT_FOUND 등)
	public String message; // 사용자 표시용 메시지
	public String detail; // 상세 원인(로그/고객센터 참고용, 선택)
}


