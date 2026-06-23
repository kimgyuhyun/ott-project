-- 인증 이벤트(감사 로그) 테이블 생성
-- 로그인/로그아웃/로그인 실패/탈퇴 등 인증 행위를 1건씩 적재하여
-- 보안 추적 및 통계 스냅샷(daily_stats)의 원천 데이터로 사용한다.
CREATE TABLE auth_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL, -- 실패/미식별 시 null 가능, 사용자 삭제 시 이력은 보존
    email VARCHAR(255),                                     -- 시도 이메일(실패 분석을 위해 항상 보관)
    event_type VARCHAR(30) NOT NULL,                        -- LOGIN_SUCCESS / LOGIN_FAIL / LOGOUT / WITHDRAW
    provider VARCHAR(20),                                   -- LOCAL / GOOGLE / NAVER / KAKAO (실패 시 null 가능)
    ip_address VARCHAR(45),                                 -- 접속 IP(IPv6 고려)
    user_agent VARCHAR(512),                                -- 접속 User-Agent
    session_id VARCHAR(128),                                -- 세션 ID
    fail_reason VARCHAR(255),                               -- 실패 사유(성공 시 null)
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP -- 발생 시각(KST 기준 값 저장)
);

-- 기간 기반 통계 집계용 인덱스
CREATE INDEX idx_auth_events_occurred ON auth_events (occurred_at);
-- 유형별 기간 집계용 인덱스(로그인 성공/실패/로그아웃 카운트)
CREATE INDEX idx_auth_events_type_occurred ON auth_events (event_type, occurred_at);
-- 사용자별 이력 조회용 인덱스
CREATE INDEX idx_auth_events_user ON auth_events (user_id, occurred_at);
