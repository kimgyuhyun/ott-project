-- V2025_08_36__create_subtitles_table.sql
-- 자막 테이블 생성

-- subtitles 테이블 생성
CREATE TABLE IF NOT EXISTS subtitles (
    id BIGSERIAL PRIMARY KEY,
    episode_id BIGINT NOT NULL,
    language VARCHAR(10) NOT NULL,
    url TEXT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 외래키 제약조건
    CONSTRAINT fk_subtitles_episode_id FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE,
    
    -- 인덱스
    CONSTRAINT uk_subtitles_episode_language UNIQUE (episode_id, language)
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_subtitles_episode_id ON subtitles(episode_id);
CREATE INDEX IF NOT EXISTS idx_subtitles_language ON subtitles(language);
CREATE INDEX IF NOT EXISTS idx_subtitles_is_default ON subtitles(is_default);

-- 기본 자막이 하나만 있도록 하는 부분 인덱스 (is_default = true인 경우)
CREATE UNIQUE INDEX IF NOT EXISTS idx_subtitles_episode_default ON subtitles(episode_id) WHERE is_default = true;

-- 테이블 코멘트
COMMENT ON TABLE subtitles IS '에피소드별 자막 파일 정보';
COMMENT ON COLUMN subtitles.id IS '자막 고유 ID';
COMMENT ON COLUMN subtitles.episode_id IS '소속 에피소드 ID';
COMMENT ON COLUMN subtitles.language IS '언어 코드 (ko, en, ja)';
COMMENT ON COLUMN subtitles.url IS '웹VTT 파일 URL';
COMMENT ON COLUMN subtitles.is_default IS '기본 자막 여부';
COMMENT ON COLUMN subtitles.created_at IS '생성 시각';
COMMENT ON COLUMN subtitles.updated_at IS '수정 시각';
