package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.config.RabbitConfig;
import com.ottproject.ottbackend.dto.BillingRetryMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * BillingRetryPublisher
 *
 * 큰 흐름
 * - 정기결제 실패 시 실패 횟수에 맞는 RabbitMQ 대기 큐(TTL+DLX)로 지연 재시도 메시지를 발행한다.
 * - 1차 실패 → first 대기 큐(기본 3시간), 2차 실패 → second 대기 큐(기본 24시간).
 * - 트랜잭션 안에서 불리면 커밋이 끝난 뒤에 발행한다(scheduleRetry 주석).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingRetryPublisher {

    private final RabbitTemplate rabbitTemplate; // JSON 컨버터 적용 템플릿(RabbitConfig)

    /**
     * 실패 횟수에 맞는 대기 큐로 지연 재시도 메시지를 발행한다. 트랜잭션 안에서 불리면 커밋 뒤에 발행한다.
     * - 커밋 전에 발행하면, 그 트랜잭션이 롤백됐을 때 기록되지 않은 실패를 근거로 재시도가 도착한다(ARCHITECTURE 13절).
     * - 그래서 발행 결과를 돌려주지 않는다. 결과는 커밋 뒤에야 나온다. 발행이 실패하면 ERROR 로 남기고,
     *   호출부가 잡아 둔 nextBillingAt(+3일)에 스윕 배치가 복구한다.
     * - 기본 교환기(빈 이름)는 라우팅 키=큐 이름으로 직행하므로 대기 큐에 바로 넣는다.
     * - MembershipNotificationService.send 와 같은 방식이다.
     * @param subscriptionId 재시도 대상 구독 PK
     * @param attempt 현재 실패 횟수(1이면 first, 2이면 second 대기 큐)
     */
    public void scheduleRetry(Long subscriptionId, int attempt) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(subscriptionId, attempt);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(subscriptionId, attempt);
            }
        });
    }

    private void publish(Long subscriptionId, int attempt) {
        String waitQueue = (attempt <= 1) ? RabbitConfig.WAIT_QUEUE_FIRST : RabbitConfig.WAIT_QUEUE_SECOND;
        try {
            rabbitTemplate.convertAndSend("", waitQueue, new BillingRetryMessageDto(subscriptionId, attempt));
            log.info(
                    "정기결제 재시도 지연 메시지 발행 - subscriptionId: {}, attempt: {}, queue: {}",
                    subscriptionId,
                    attempt,
                    waitQueue);
        } catch (Exception e) {
            // 커밋이 이미 끝나 되돌릴 것이 없다. 재시도는 스윕 배치가 nextBillingAt(+3일)에 이어받는다.
            log.error(
                    "정기결제 재시도 메시지 발행 실패(스윕이 다음 청구일에 복구) - subscriptionId: {}, attempt: {}", subscriptionId, attempt, e);
        }
    }
}
