package com.ottproject.ottbackend.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * BillingRetryPublisher 발행 시점 단위 테스트
 *
 * 지키려는 규칙
 * - 트랜잭션 안에서 부른 재시도 발행은 커밋이 끝난 뒤에 나가고, 롤백되면 나가지 않는다(ARCHITECTURE 13절).
 *   커밋 전에 나가면, 롤백돼 기록되지 않은 실패를 근거로 재시도 메시지가 도착한다.
 * - 커밋 뒤의 발행 실패는 호출부로 나가지 않는다. 스윕 배치가 다음 청구일에 복구한다.
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
    @DisplayName("트랜잭션 안에서 부르면 커밋 전에는 발행하지 않고, 커밋 뒤에 실패 횟수에 맞는 대기 큐로 발행한다")
    void publishesOnlyAfterCommitInsideTransaction() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.scheduleRetry(SUB_ID, 1);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        commit();
        verify(rabbitTemplate)
                .convertAndSend(eq(""), eq(RabbitConfig.WAIT_QUEUE_FIRST), any(BillingRetryMessageDto.class));
    }

    @Test
    @DisplayName("롤백되면 발행하지 않는다")
    void doesNotPublishWhenRolledBack() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.scheduleRetry(SUB_ID, 2);
        rollback();

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("커밋 뒤 발행이 실패해도 예외가 호출부로 나가지 않는다")
    void doesNotPropagatePublishFailureAfterCommit() {
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));
        TransactionSynchronizationManager.initSynchronization();

        assertThatCode(() -> {
                    publisher.scheduleRetry(SUB_ID, 2);
                    commit();
                })
                .doesNotThrowAnyException();
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private void rollback() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }
}
