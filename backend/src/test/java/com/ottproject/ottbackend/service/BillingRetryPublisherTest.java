package com.ottproject.ottbackend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.ottproject.ottbackend.config.RabbitConfig;
import com.ottproject.ottbackend.dto.BillingRetryMessageDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * BillingRetryPublisher 발행 시점 단위 테스트
 *
 * 결함 기록(2026-09-26)
 * - 트랜잭션 안에서 불려도 재시도 메시지를 그 자리에서 발행한다(ARCHITECTURE 13절은 커밋 전 발행을 금지한다).
 *   그 트랜잭션이 롤백되면, 기록되지 않은 실패를 근거로 재시도 메시지가 도착한다.
 * - 아래 "(현재 결함)" 테스트는 이 동작을 기록한다. 수정 커밋이 뒤집는다.
 *
 * 트랜잭션은 TransactionSynchronizationManager 로 흉내 낸다(MembershipNotificationServiceTest 와 같은 방식).
 */
@ExtendWith(MockitoExtension.class)
class BillingRetryPublisherTest {

    private static final long SUB_ID = 10L;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private BillingRetryPublisher publisher;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션 안에서 불러도 커밋 전에 바로 발행한다(현재 결함)")
    void publishesBeforeCommitInsideTransaction() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.scheduleRetry(SUB_ID, 1);

        // 결함: 커밋이 끝나기 전에 이미 나갔다
        verify(rabbitTemplate)
                .convertAndSend(eq(""), eq(RabbitConfig.WAIT_QUEUE_FIRST), any(BillingRetryMessageDto.class));
    }

    @Test
    @DisplayName("롤백돼도 재시도 메시지는 이미 나간 뒤다(현재 결함)")
    void hasAlreadyPublishedWhenRolledBack() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.scheduleRetry(SUB_ID, 2);
        rollback();

        // 결함: 실패 기록은 롤백됐는데 그 실패를 근거로 한 재시도가 대기 큐에 들어갔다
        verify(rabbitTemplate)
                .convertAndSend(eq(""), eq(RabbitConfig.WAIT_QUEUE_SECOND), any(BillingRetryMessageDto.class));
    }

    private void rollback() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }
}
