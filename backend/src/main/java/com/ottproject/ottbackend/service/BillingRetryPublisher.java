package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.config.RabbitConfig;
import com.ottproject.ottbackend.dto.BillingRetryMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * BillingRetryPublisher
 *
 * 큰 흐름
 * - 정기결제 실패 시 실패 횟수에 맞는 RabbitMQ 대기 큐(TTL+DLX)로 지연 재시도 메시지를 발행한다.
 * - 1차 실패 → first 대기 큐(기본 3시간), 2차 실패 → second 대기 큐(기본 24시간).
 * - 브로커 장애 등으로 발행에 실패하면 false를 반환해 호출부가 기존 스윕 방식으로 폴백하게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingRetryPublisher {

	private final RabbitTemplate rabbitTemplate; // JSON 컨버터 적용 템플릿(RabbitConfig)

	/**
	 * 실패 횟수에 맞는 대기 큐로 지연 재시도 메시지 발행
	 * - 기본 교환기(빈 이름)는 라우팅 키=큐 이름으로 직행하므로 대기 큐에 바로 넣는다.
	 * @param subscriptionId 재시도 대상 구독 PK
	 * @param attempt 현재 실패 횟수(1이면 first, 2이면 second 대기 큐)
	 * @return 발행 성공 여부(실패 시 호출부가 스윕 폴백을 유지)
	 */
	public boolean scheduleRetry(Long subscriptionId, int attempt) {
		String waitQueue = (attempt <= 1) ? RabbitConfig.WAIT_QUEUE_FIRST : RabbitConfig.WAIT_QUEUE_SECOND;
		try {
			rabbitTemplate.convertAndSend("", waitQueue, new BillingRetryMessageDto(subscriptionId, attempt));
			log.info("정기결제 재시도 지연 메시지 발행 - subscriptionId: {}, attempt: {}, queue: {}",
					subscriptionId, attempt, waitQueue);
			return true;
		} catch (Exception e) {
			log.warn("정기결제 재시도 메시지 발행 실패(스윕 폴백 유지) - subscriptionId: {}, attempt: {}",
					subscriptionId, attempt, e);
			return false;
		}
	}
}
