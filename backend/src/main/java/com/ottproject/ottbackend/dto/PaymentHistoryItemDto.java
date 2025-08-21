package com.ottproject.ottbackend.dto; // DTO 패키지 선언

import java.time.LocalDateTime;
import com.ottproject.ottbackend.enums.PaymentStatus;

/**
 * 결제 이력 항목 응답 DTO
 *
 * 큰 흐름
 * - 결제/환불 내역 목록의 단일 행을 표현한다.
 * - 플랜 코드/금액/통화/상태/영수증 URL/시각 정보를 포함한다.
 *
 * 필드 개요
 * - paymentId/planCode/planName: 식별/플랜 정보
 * - amount/currency/status/receiptUrl: 금액/통화/상태/영수증 링크
 * - paidAt/refundedAt: 시각 정보
 */
public class PaymentHistoryItemDto { // 결제 이력 단일 항목 DTO 클래스 시작
	public Long paymentId; // 결제 레코드 ID
	public String planCode; // 플랜 코드
	public String planName; // 플랜 이름
	public Long amount; // 결제 금액(최소 화폐단위)
	public String currency; // 통화 코드(ISO 4217)
	public PaymentStatus status; // 결제 상태
	public String receiptUrl; // 영수증 URL
	public LocalDateTime paidAt; // 결제 완료 시각(있을 때)
	public LocalDateTime refundedAt; // 환불 완료 시각(있을 때)
}
