package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.config.RabbitConfig;
import com.ottproject.ottbackend.dto.BillingRetryMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * BillingRetryConsumer
 *
 * 큰 흐름
 * - billing.retry.q(대기 큐 TTL 만료 후 DLX를 타고 도착)를 구독해
 *   해당 구독 한 건만 정기결제 재청구를 트리거한다.
 * - 폴링 없이 "실패 시점 + 지연 시간"에 정확히 도착하는 건별 재시도.
 * - 처리 중 예외는 로깅 후 폐기(default-requeue-rejected=false)하고,
 *   스윕 배치의 안전망(nextBillingAt)이 최후 복구를 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingRetryConsumer {

	private final RecurringBillingService recurringBillingService; // 재청구 로직 위임

	@RabbitListener(queues = RabbitConfig.RETRY_QUEUE)
	public void onBillingRetry(BillingRetryMessageDto message) {
		log.info("정기결제 재시도 메시지 수신 - subscriptionId: {}, attempt: {}",
				message.getSubscriptionId(), message.getAttempt());
		try {
			recurringBillingService.retryBilling(message.getSubscriptionId(), message.getAttempt());
		} catch (Exception e) {
			// 재큐잉하지 않고 폐기 → 무한 루프 방지. 스윕 안전망이 이후 주기에서 복구한다.
			log.error("정기결제 재시도 처리 실패 - subscriptionId: {}, attempt: {}",
					message.getSubscriptionId(), message.getAttempt(), e);
		}
	}
}
