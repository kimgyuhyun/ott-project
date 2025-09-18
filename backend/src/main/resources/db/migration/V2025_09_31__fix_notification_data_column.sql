-- notifications 테이블의 data 컬럼을 TEXT로 변경 (JSONB 대신)
-- Hibernate가 String을 JSONB로 자동 변환하지 못하는 문제 해결

-- 기존 데이터 백업
CREATE TABLE notifications_backup AS SELECT * FROM notifications;

-- data 컬럼을 TEXT로 변경
ALTER TABLE notifications ALTER COLUMN data TYPE TEXT;

-- 인덱스 재생성 (JSONB 관련 인덱스 제거)
DROP INDEX IF EXISTS idx_notifications_data_gin;
