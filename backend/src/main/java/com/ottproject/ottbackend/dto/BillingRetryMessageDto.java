package com.ottproject.ottbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 정기결제 재시도 지연 메시지 페이로드
 *
 * 큰 흐름
 * - 정기결제 실패 시 RabbitMQ 대기 큐(TTL+DLX)에 적재되고,
 *   TTL 만료 후 작업 큐로 이동해 해당 구독 한 건만 재청구를 트리거한다.
 *
 * 필드 개요
 * - subscriptionId: 재시도 대상 구독 PK
 * - attempt: 이 메시지를 발행한 시점의 실패 횟수(retryCount) → 소비 시점 정합성 검증용
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingRetryMessageDto {
    private Long subscriptionId; // 재시도 대상 구독 PK
    private int attempt; // 발행 시점의 실패 횟수(스테일 메시지 판별)
}
