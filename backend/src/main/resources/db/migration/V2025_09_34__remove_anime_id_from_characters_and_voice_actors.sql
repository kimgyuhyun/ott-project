-- Character와 VoiceActor 테이블에서 anime_id 컬럼 제거
-- anime_characters, anime_voice_actors 조인 테이블로 관계를 관리하므로 불필요한 컬럼

-- 외래키 제약조건 먼저 제거
ALTER TABLE characters DROP CONSTRAINT IF EXISTS fk_characters_anime_id;
ALTER TABLE voice_actors DROP CONSTRAINT IF EXISTS fk_voice_actors_anime_id;

-- 인덱스 제거
DROP INDEX IF EXISTS idx_characters_anime_id;
DROP INDEX IF EXISTS idx_voice_actors_anime_id;

-- anime_id 컬럼 제거
ALTER TABLE characters DROP COLUMN IF EXISTS anime_id;
ALTER TABLE voice_actors DROP COLUMN IF EXISTS anime_id;
