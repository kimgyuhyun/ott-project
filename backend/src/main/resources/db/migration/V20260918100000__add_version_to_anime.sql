-- 관리자 단건 큐레이션 수정의 낙관적 락 버전
-- 수정 폼을 연 뒤 다른 관리자가 먼저 저장하면 옛 화면 기준 저장이 그 수정을 덮어쓰던 결함을 막는다.
-- DEFAULT 가 있는 NOT NULL 추가라 기존 행은 0 으로 채워지고, 아직 이 컬럼을 모르는 구버전 인스턴스의 INSERT 도 통과한다.
ALTER TABLE anime ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
