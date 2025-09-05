-- plans 테이블에 is_active 컬럼 추가
-- MembershipPlan 엔티티의 isActive 필드와 매핑

-- 1. is_active 컬럼 추가 (기본값 true)
ALTER TABLE plans ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;

-- 2. 기존 데이터에 대한 기본값 설정 (모든 기존 플랜을 활성화 상태로)
UPDATE plans SET is_active = true WHERE is_active IS NULL;

-- 3. 인덱스 추가 (활성 플랜 조회 성능 최적화)
CREATE INDEX idx_plans_is_active ON plans(is_active);

-- 4. 활성 플랜 조회를 위한 복합 인덱스
CREATE INDEX idx_plans_active_code ON plans(is_active, code);
