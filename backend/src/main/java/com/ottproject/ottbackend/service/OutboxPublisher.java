package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.OutboxEvent;
import com.ottproject.ottbackend.enums.OutboxStatus;
import com.ottproject.ottbackend.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * OutboxPublisher
 *
 * 큰 흐름
 * - 아웃박스 테이블의 NEW 이벤트를 주기적으로 폴링하여 카프카로 발행한다.
 * - 발행 성공 시에만 PUBLISHED 로 전환한다(실패분은 다음 주기에 재발행 → at-least-once).
 * - 컨슈머 측 멱등 처리(eventId 기준)와 짝을 이뤄 "정확히 한 번 처리"에 준하는 효과를 낸다.
 *
 * 설계 메모
 * - 결제 확정 트랜잭션과 발행을 분리(아웃박스 패턴)하여 dual-write 유실을 방지한다.
 * - 메시지 키 = aggregateId(결제 PK)로 지정해 동일 애그리거트 이벤트의 파티션 순서를 보장한다.
 * - eventId 는 payload(JSON) 안에 포함되어 컨슈머로 전파된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository; // 아웃박스 리포지토리
    private final KafkaTemplate<Object, Object> kafkaTemplate; // 카프카 발행 템플릿(String 직렬화)

    private static final int BATCH_SIZE = 100; // 한 주기 최대 발행 건수

    /**
     * 미발행(NEW) 이벤트 폴링 발행
     * - 기본 2초 주기(outbox.publish-interval-ms 로 조정)
     */
    @Scheduled(fixedDelayString = "${outbox.publish-interval-ms:2000}")
    public void publishPending() {
        List<OutboxEvent> batch = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.NEW, PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return;

        for (OutboxEvent e : batch) {
            try {
                // 동기 발행(.get())으로 성공을 확인한 뒤에만 PUBLISHED 로 마킹한다.
                kafkaTemplate.send(e.getTopic(), e.getAggregateId(), e.getPayload()).get();
                e.markPublished();
                outboxEventRepository.save(e);
                log.debug("아웃박스 발행 완료 - eventId: {}, topic: {}", e.getEventId(), e.getTopic());
            } catch (Exception ex) {
                // 마킹하지 않으므로 다음 폴링 주기에 재시도된다(at-least-once).
                log.error("아웃박스 발행 실패 - eventId: {} (다음 주기 재시도)", e.getEventId(), ex);
            }
        }
    }
}
