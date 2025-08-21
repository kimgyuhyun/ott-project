package com.ottproject.ottbackend.dto; // DTO 패키지 선언

import java.time.LocalDateTime;
import com.ottproject.ottbackend.enums.PaymentStatus;

/**
 * 결제 결과 단건 응답 DTO
 *
 * 큰 흐름
 * - 결제 최종 결과 단건을 표현한다(성공/실패/취소/환불).
 *
 * 필드 개요
 * - paymentId/status/providerPaymentId/receiptUrl/reasonCode/message/occurredAt
 */
public class PaymentResultResponseDto { // 결제 결과 단건 응답 DTO 클래스 시작
	public Long paymentId; // 내부 결제 레코드 ID
	public PaymentStatus status; // 최종 상태(SUCCEEDED/FAILED/CANCELED/REFUNDED)
	public String providerPaymentId; // 외부 결제 식별자(승인 후)
	public String receiptUrl; // 영수증 URL(성공 시)
	public String reasonCode; // 실패/취소 사유 코드(선택)
	public String message; // 사유 설명(선택)
	public LocalDateTime occurredAt; // 상태가 결정된 시각
}


