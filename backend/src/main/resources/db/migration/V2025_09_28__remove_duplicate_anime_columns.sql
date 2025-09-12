-- Remove duplicate columns from anime table
-- These columns are now managed through many-to-many relationship tables

-- 1. Remove voice_actors column (now managed via anime_voice_actors table)
ALTER TABLE anime DROP COLUMN IF EXISTS voice_actors;

-- 2. Remove director column (now managed via anime_directors table)  
ALTER TABLE anime DROP COLUMN IF EXISTS director;

-- Add comments for documentation
COMMENT ON TABLE anime IS '애니메이션 정보 테이블 - director와 voice_actors는 중간테이블로 관리';
