package com.ottproject.ottbackend.dto; // DTO 패키지 선언

import java.time.LocalDateTime;

/**
 * 결제 취소 응답 DTO
 *
 * 역할:
 * - 사용자/시스템에 의해 결제가 취소된 결과를 단건으로 반환
 * - 즉시 취소 UX나 콜백 처리 후 화면 표시용
 */
public class PaymentCancelResponseDto { // 결제 취소 응답 DTO 클래스 시작
	public Long paymentId; // 내부 결제 레코드 ID
	public String providerPaymentId; // 외부 결제 식별자(선택)
	public LocalDateTime canceledAt; // 취소 완료 시각
	public String reasonCode; // 취소 사유 코드(선택)
	public String message; // 취소 사유 설명(선택)
}


