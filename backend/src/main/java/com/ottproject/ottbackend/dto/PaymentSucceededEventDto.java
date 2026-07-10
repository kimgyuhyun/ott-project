package com.ottproject.ottbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 결제 성공 도메인 이벤트 페이로드
 *
 * 큰 흐름
 * - 결제 확정 시 아웃박스에 적재되어 카프카로 발행되는 이벤트 본문.
 * - 부수효과 컨슈머(영수증 메일/통계/추천)가 이 정보만으로 처리할 수 있도록 필요한 값만 담는다.
 *
 * 필드 개요
 * - eventId: 이벤트 고유 식별자(UUID) → 컨슈머 멱등 키
 * - paymentId/userId/planCode/amount/paidAt: 결제 식별/사용자/플랜/금액/결제 시각
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSucceededEventDto {
    private String eventId; // 이벤트 고유 식별자(UUID)
    private Long paymentId; // 결제 PK
    private Long userId; // 사용자 ID
    private String planCode; // 멤버십 플랜 코드
    private Long amount; // 결제 금액(최소 화폐단위)
    private LocalDateTime paidAt; // 결제 시각
}
