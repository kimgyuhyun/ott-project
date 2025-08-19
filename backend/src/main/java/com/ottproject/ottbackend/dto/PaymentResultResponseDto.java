package com.ottproject.ottbackend.dto; // DTO 패키지 선언

import java.time.LocalDateTime;
import com.ottproject.ottbackend.enums.PaymentStatus;

/**
 * 결제 결과 단건 응답 DTO
 *
 * 역할:
 * - 결제 성공/실패/취소/환불 중 하나의 최종 결과를 단건으로 반환
 * - 리다이렉트 콜백 이후 결과 페이지 또는 단건 조회 API에 사용
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


