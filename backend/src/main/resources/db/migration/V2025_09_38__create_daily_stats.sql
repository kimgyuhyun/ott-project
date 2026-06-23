-- 일일 통계 스냅샷 테이블 생성
-- 인증 이벤트(auth_events)와 가입 정보를 하루 단위로 집계한 결과를 1행으로 보관한다.
-- 관리자 통계 화면은 이 테이블만 조회하면 되므로 raw 이벤트 풀스캔을 피할 수 있다.
CREATE TABLE daily_stats (
    id BIGSERIAL PRIMARY KEY,
    stat_date DATE NOT NULL UNIQUE,                  -- 통계 기준 일자(고유, upsert 키)
    login_success_count BIGINT NOT NULL DEFAULT 0,   -- 로그인 성공 건수
    login_fail_count BIGINT NOT NULL DEFAULT 0,      -- 로그인 실패 건수
    logout_count BIGINT NOT NULL DEFAULT 0,          -- 로그아웃 건수
    signup_count BIGINT NOT NULL DEFAULT 0,          -- 신규 가입자 수
    active_user_count BIGINT NOT NULL DEFAULT 0,     -- DAU(로그인 성공 고유 사용자 수)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 최초 생성 시각
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP  -- 마지막 갱신 시각
);

-- 기간 조회용 인덱스(차트/목록)
CREATE INDEX idx_daily_stats_date ON daily_stats (stat_date);
