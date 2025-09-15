-- episode_progress 테이블에 hidden_in_recent 컬럼 추가
-- 최근본 목록에서 숨김 처리용 (시청 기록은 유지)

-- hidden_in_recent 컬럼 추가 (기본값 false)
ALTER TABLE episode_progress 
ADD COLUMN hidden_in_recent BOOLEAN NOT NULL DEFAULT false;

-- 기존 데이터는 모두 false로 설정 (이미 시청한 기록들은 목록에 표시)
UPDATE episode_progress 
SET hidden_in_recent = false 
WHERE hidden_in_recent IS NULL;

-- 인덱스 추가 (사용자별 숨김 여부 조회 최적화)
CREATE INDEX idx_episode_progress_user_hidden 
ON episode_progress(user_id, hidden_in_recent);

-- 성능 모니터링을 위한 통계 업데이트
ANALYZE episode_progress;
