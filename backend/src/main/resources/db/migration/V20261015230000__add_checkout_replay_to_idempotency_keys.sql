-- 체크아웃 멱등키에 첫 응답 재생에 필요한 값을 남긴다(#191, ARCHITECTURE 6절).
-- 같은 키로 다시 온 체크아웃에 409 대신 첫 응답(결제 ID·PG 세션)을 그대로 돌려주려면 응답 본문이 있어야 하고,
-- 남의 키로 남의 응답을 받거나 같은 키로 다른 내용을 보내는 것을 막으려면 소유자와 요청 지문이 있어야 한다.

-- 셋 다 NULL 허용이다. 해지·환불·웹훅 키는 이 값을 쓰지 않고, 롤링 배포 중 구버전 인스턴스는 이 컬럼을
-- 모른 채 INSERT 한다(새 컬럼 추가는 expand 단계라 구버전을 깨뜨리지 않는다).
-- user_id 에 외래 키를 걸지 않는다: 재생 판정에 쓰는 값일 뿐이고, 탈퇴 정리가 이 테이블까지 따라올 이유가 없다.
ALTER TABLE idempotency_keys
    ADD COLUMN user_id BIGINT,
    ADD COLUMN request_fingerprint VARCHAR(64),
    ADD COLUMN response_body TEXT;
