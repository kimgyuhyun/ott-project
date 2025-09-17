-- 알림 시스템 테이블 생성
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    data JSONB,
    is_read BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 인덱스 생성 (사용자별 읽지 않은 알림 빠른 조회)
CREATE INDEX idx_notifications_user_unread ON notifications (user_id, is_read, created_at);

-- 기존 user_settings 테이블에 알림 설정 컬럼 추가
ALTER TABLE user_settings ADD COLUMN notification_work_updates BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE user_settings ADD COLUMN notification_community_activity BOOLEAN DEFAULT TRUE NOT NULL;

-- 기존 데이터에 기본값 설정
UPDATE user_settings SET 
    notification_work_updates = TRUE,
    notification_community_activity = TRUE 
WHERE notification_work_updates IS NULL OR notification_community_activity IS NULL;
