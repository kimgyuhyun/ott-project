package com.ottproject.ottbackend.dto; // DTO 패키지 선언

/**
 * 체크아웃(결제창) 생성 실패 응답 DTO
 *
 * 역할:
 * - 체크아웃 생성 과정에서 동기적으로 발생한 오류 정보를 반환
 * - 클라이언트가 사용자 메시지와 재시도/대체 행동을 결정할 수 있도록 코드/메시지를 제공
 */
public class PaymentCheckoutCreateErrorResponseDto { // 체크아웃 생성 실패 응답 DTO 클래스 시작
	public String code; // 표준 오류 코드(예: CHECKOUT_CREATE_FAILED, PLAN_NOT_FOUND 등)
	public String message; // 사용자 표시용 메시지
	public String detail; // 상세 원인(로그/고객센터 참고용, 선택)
}


