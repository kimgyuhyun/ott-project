package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 트랜잭셔널 아웃박스 이벤트 엔티티
 *
 * 큰 흐름
 * - 결제 확정 등 도메인 변경과 "같은 트랜잭션"으로 이벤트를 이 테이블에 저장한다(dual-write 문제 회피).
 * - 별도 발행기(OutboxPublisher)가 NEW 상태 행을 폴링하여 카프카로 발행하고 PUBLISHED 로 전환한다.
 * - eventId 는 카프카 메시지 키/헤더로 전파되어 컨슈머 멱등 처리(중복 배달 방어)의 기준이 된다.
 *
 * 필드 개요
 * - id: PK
 * - eventId: 이벤트 고유 식별자(UUID, 발행 후에도 불변 → 컨슈머 멱등 키)
 * - aggregateType/aggregateId: 애그리거트 종류/식별자(e.g., Payment / 결제 PK)
 * - eventType: 이벤트 종류(e.g., PaymentSucceeded)
 * - topic: 발행 대상 카프카 토픽
 * - payload: 직렬화된 이벤트 본문(JSON)
 * - status: 발행 상태(NEW/PUBLISHED)
 * - createdAt/publishedAt: 적재/발행 시각
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "ux_outbox_event_id", columnList = "event_id", unique = true),
        @Index(name = "idx_outbox_status_created", columnList = "status, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId; // 이벤트 고유 식별자(UUID) - 컨슈머 멱등 키

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType; // 애그리거트 종류(e.g., Payment)

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId; // 애그리거트 식별자(e.g., 결제 PK)

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType; // 이벤트 종류(e.g., PaymentSucceeded)

    @Column(nullable = false, length = 128)
    private String topic; // 발행 대상 카프카 토픽

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // 직렬화된 이벤트 본문(JSON)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxStatus status; // 발행 상태

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt; // 적재 시각

    @Column(name = "published_at")
    private LocalDateTime publishedAt; // 발행 시각

    /**
     * 아웃박스 이벤트 생성(정적 팩토리)
     * - 상태는 NEW, 적재 시각은 현재로 초기화한다.
     */
    public static OutboxEvent create(String aggregateType, String aggregateId,
                                     String eventType, String topic,
                                     String eventId, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.eventId = eventId;
        e.aggregateType = aggregateType;
        e.aggregateId = aggregateId;
        e.eventType = eventType;
        e.topic = topic;
        e.payload = payload;
        e.status = OutboxStatus.NEW;
        e.createdAt = LocalDateTime.now();
        return e;
    }

    /**
     * 발행 완료 처리
     */
    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }
}
