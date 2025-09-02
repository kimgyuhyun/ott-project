-- 멤버십 플랜 변경 기능을 위한 컬럼 추가
-- subscriptions 테이블에 플랜 변경 관련 필드 3개 추가

-- 1. 컬럼 추가
ALTER TABLE subscriptions ADD COLUMN next_plan_id BIGINT;
ALTER TABLE subscriptions ADD COLUMN plan_change_scheduled_at TIMESTAMP;
ALTER TABLE subscriptions ADD COLUMN change_type VARCHAR(20);

-- 2. 외래키 제약조건 추가
ALTER TABLE subscriptions 
ADD CONSTRAINT fk_subscriptions_next_plan 
FOREIGN KEY (next_plan_id) REFERENCES plans(id);

-- 3. 인덱스 추가 (검색 성능 최적화)
CREATE INDEX idx_subscriptions_next_plan_id ON subscriptions(next_plan_id);
CREATE INDEX idx_subscriptions_plan_change_scheduled ON subscriptions(plan_change_scheduled_at);
CREATE INDEX idx_subscriptions_change_type ON subscriptions(change_type);

-- 4. 기존 데이터에 대한 기본값 설정 (모두 NULL로 초기화)
-- 새로 추가된 컬럼들은 기본적으로 NULL 허용이므로 별도 설정 불필요
