-- 멱등키에 선점 상태를 추가한다.
-- 환불은 게이트웨이 호출 전에 키를 커밋하므로, 호출이 실패해도 키가 남아 재환불이 영구히 막혔다.
-- 대사 배치가 "선점만 되고 확정되지 않은" 키를 골라내 게이트웨이에 역조회할 수 있게 상태를 남긴다.

-- 기본값은 CONFIRMED 다. 기존 행은 물론이고, 롤링 배포 중 구버전 인스턴스가 status 없이 INSERT 하는
-- 행까지 배치 대상에서 빠진다(구버전은 확정 전이를 할 줄 모르므로 CLAIMED 로 들어가면 영영 안 풀린다).
ALTER TABLE idempotency_keys
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED';

-- 배치 조회용. 이 테이블은 체크아웃/웹훅 키까지 무한 누적되는데 key_value 유니크 인덱스뿐이라
-- (purpose, status, created_at) 조회가 풀스캔이 된다.
CREATE INDEX idx_idempotency_keys_purpose_status_created
    ON idempotency_keys(purpose, status, created_at);
