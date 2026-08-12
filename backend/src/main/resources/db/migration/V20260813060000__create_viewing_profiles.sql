-- 시청 프로필 테이블 생성
-- 계정 하나에 여러 시청 프로필을 두고, 로그인 후 사용할 프로필을 고르게 한다.
-- 이번 단계는 프로필 자체만 만든다. 시청기록·찜·별점은 여전히 user_id 에 붙어 있으므로
-- 프로필을 바꿔도 보이는 데이터는 같다. 데이터 분리는 별도 작업이다.
CREATE TABLE viewing_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, -- 소유 계정(계정이 지워지면 프로필도 삭제)
    name VARCHAR(20) NOT NULL,                                      -- 프로필 표시 이름
    created_at TIMESTAMP NOT NULL,                                  -- 생성 시각
    updated_at TIMESTAMP NOT NULL                                   -- 갱신 시각
);

-- 외래 키 컬럼 인덱스. PostgreSQL 은 외래 키에 인덱스를 자동 생성하지 않는다.
-- 조회는 항상 "내 계정의 프로필 전체"라 user_id 단일 컬럼이면 충분하다.
CREATE INDEX idx_viewing_profiles_user_id ON viewing_profiles (user_id);
