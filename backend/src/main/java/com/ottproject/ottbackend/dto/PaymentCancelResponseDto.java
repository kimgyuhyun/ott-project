package com.ottproject.ottbackend.dto; // DTO 패키지 선언

import java.time.LocalDateTime;

/**
 * 결제 취소 응답 DTO
 *
 * 큰 흐름
 * - 취소 결과 단건을 표현한다(취소 시각/사유 포함).
 *
 * 필드 개요
 * - paymentId/providerPaymentId/canceledAt/reasonCode/message
 */
public class PaymentCancelResponseDto { // 결제 취소 응답 DTO 클래스 시작
	public Long paymentId; // 내부 결제 레코드 ID
	public String providerPaymentId; // 외부 결제 식별자(선택)
	public LocalDateTime canceledAt; // 취소 완료 시각
	public String reasonCode; // 취소 사유 코드(선택)
	public String message; // 취소 사유 설명(선택)
}


