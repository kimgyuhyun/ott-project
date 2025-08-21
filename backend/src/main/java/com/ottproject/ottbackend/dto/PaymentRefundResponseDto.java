package com.ottproject.ottbackend.dto; // DTO 패키지 선언

import java.time.LocalDateTime;

/**
 * 환불 응답 DTO
 *
 * 큰 흐름
 * - 전액/부분 환불 결과 단건을 표현한다.
 *
 * 필드 개요
 * - paymentId/providerRefundId/refundedAmount/refundedAt/message
 */
public class PaymentRefundResponseDto { // 환불 응답 DTO 클래스 시작
	public Long paymentId; // 내부 결제 레코드 ID
	public String providerRefundId; // 외부 환불 식별자(선택)
	public Long refundedAmount; // 환불 금액(최소 화폐단위)
	public LocalDateTime refundedAt; // 환불 완료 시각
	public String message; // 환불 메모/사유(선택)
}


