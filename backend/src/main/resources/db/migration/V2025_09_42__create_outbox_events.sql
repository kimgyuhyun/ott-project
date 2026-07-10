-- 트랜잭셔널 아웃박스 테이블 생성
-- 결제 확정 등 도메인 변경과 같은 트랜잭션으로 이벤트를 적재하여(dual-write 회피),
-- 별도 발행기(OutboxPublisher)가 NEW 행을 폴링해 카프카로 발행한 뒤 PUBLISHED 로 전환한다.
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,          -- 이벤트 고유 식별자(UUID, 컨슈머 멱등 키)
    aggregate_type VARCHAR(64) NOT NULL,           -- 애그리거트 종류(e.g., Payment)
    aggregate_id VARCHAR(64) NOT NULL,             -- 애그리거트 식별자(e.g., 결제 PK)
    event_type VARCHAR(64) NOT NULL,               -- 이벤트 종류(e.g., PaymentSucceeded)
    topic VARCHAR(128) NOT NULL,                   -- 발행 대상 카프카 토픽
    payload TEXT NOT NULL,                          -- 직렬화된 이벤트 본문(JSON)
    status VARCHAR(16) NOT NULL,                    -- 발행 상태(NEW/PUBLISHED)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 적재 시각
    published_at TIMESTAMP                          -- 발행 시각
);

-- 발행 대기(NEW) 폴링용 인덱스: 상태 + 생성순
CREATE INDEX idx_outbox_status_created ON outbox_events (status, created_at);
