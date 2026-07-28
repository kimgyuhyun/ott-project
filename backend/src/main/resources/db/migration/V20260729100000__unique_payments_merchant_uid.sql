-- payments.provider_session_id(= merchant_uid)에 유니크 제약 추가
--
-- 왜 필요한가
-- 정기결제 재청구가 PG 호출 전에 결정적 merchant_uid 로 PENDING 을 선삽입하도록 바뀌었다.
-- 이 제약이 없으면 MQ 중복 배달 두 건이 둘 다 삽입에 성공해 결제 API 가 두 번 호출된다.
-- 즉 이 인덱스가 이중 청구를 막는 1차 방어선이다(비관적 락은 우리 DB 안의 동시성만 막는다).
--
-- 기존 데이터 영향
-- NULL 은 PostgreSQL 유니크 인덱스에서 서로 충돌하지 않으므로 값이 없는 행은 그대로 둔다.
-- 기존 값은 체크아웃(order_*)과 차액결제(proration_*) 경로가 만든 것으로 이미 유일하다.
-- 만약 중복이 있으면 이 마이그레이션이 실패한다 — 조용히 넘어가는 것보다 낫다.
--   확인: SELECT provider_session_id, count(*) FROM payments
--         WHERE provider_session_id IS NOT NULL GROUP BY 1 HAVING count(*) > 1;

DROP INDEX IF EXISTS idx_payments_merchant_uid; -- 아래 유니크 인덱스가 조회도 커버하므로 중복

CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_merchant_uid
    ON payments (provider_session_id);
