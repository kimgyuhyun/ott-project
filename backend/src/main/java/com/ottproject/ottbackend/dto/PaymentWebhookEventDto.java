package com.ottproject.ottbackend.dto; // DTO 패키지 선언

import java.time.LocalDateTime;
import com.ottproject.ottbackend.enums.PaymentStatus;

/**
 * 결제 Webhook 이벤트 수신 DTO
 *
 * 큰 흐름
 * - 게이트웨이에서 전달하는 결과 이벤트를 내부 공통 포맷으로 수신한다.
 * - 시그니처/재검증은 상위 계층(컨트롤러/서비스)에서 처리한다.
 *
 * 필드 개요
 * - eventId/providerPaymentId/providerSessionId: 이벤트/외부 식별자
 * - status/amount/currency/receiptUrl/occurredAt: 상태/금액/통화/영수증/발생시각
 */
public class PaymentWebhookEventDto { // 웹훅 이벤트 수신 DTO 클래스 시작
	public String eventId; // 게이트웨이 이벤트 고유 ID(멱등 처리용)
	public String providerPaymentId; // 외부 결제 식별자
	public String providerSessionId; // 외부 체크아웃/세션 식별자
	public PaymentStatus status; // 상태(SUCCEEDED/FAILED/CANCELED/REFUNDED 등)
	public Long amount; // 금액(최소 화폐단위)
	public String currency; // 통화 코드(ISO 4217)
	public String receiptUrl; // 영수증 URL
	public LocalDateTime occurredAt; // 이벤트 발생 시각(게이트웨이 기준)
}
