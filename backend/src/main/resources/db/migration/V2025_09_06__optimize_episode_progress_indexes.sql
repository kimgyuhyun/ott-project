-- episode_progress 테이블 성능 최적화를 위한 인덱스 추가
-- 복합 인덱스로 조회 성능 향상

-- 기존 단일 인덱스 제거 (복합 인덱스가 더 효율적)
DROP INDEX IF EXISTS idx_episode_progress_user_id;

-- 사용자별 에피소드 진행률 조회 최적화 (가장 중요한 인덱스)
-- (user_id, episode_id) 복합 인덱스는 user_id 단일 조회도 커버함
CREATE INDEX IF NOT EXISTS idx_episode_progress_user_episode 
ON episode_progress(user_id, episode_id);

-- 최근 시청 기록 조회 최적화 (마이페이지용)
CREATE INDEX IF NOT EXISTS idx_episode_progress_user_updated 
ON episode_progress(user_id, updated_at DESC);

-- 에피소드별 진행률 통계 조회 최적화
CREATE INDEX IF NOT EXISTS idx_episode_progress_episode 
ON episode_progress(episode_id);

-- 데이터 무결성 검증을 위한 체크 제약조건 추가
-- 기존 데이터와 호환되도록 조건 완화

-- position_sec은 0 이상이어야 함 (기본값 0과 호환)
ALTER TABLE episode_progress 
ADD CONSTRAINT chk_episode_progress_position_positive 
CHECK (position_sec >= 0);

-- duration_sec은 0 이상이어야 함 (기본값 0과 호환, 0이면 아직 로드되지 않은 상태)
ALTER TABLE episode_progress 
ADD CONSTRAINT chk_episode_progress_duration_non_negative 
CHECK (duration_sec >= 0);

-- duration_sec이 0보다 클 때만 position_sec이 duration_sec을 초과하지 않도록 검증
ALTER TABLE episode_progress 
ADD CONSTRAINT chk_episode_progress_position_not_exceed_duration 
CHECK (duration_sec = 0 OR position_sec <= duration_sec);

-- 성능 모니터링을 위한 통계 업데이트
ANALYZE episode_progress;
