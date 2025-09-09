-- Character와 VoiceActor 테이블의 anime_id 컬럼을 NULL 허용으로 변경
-- 기존 NOT NULL 제약조건을 제거하고 NULL 허용으로 변경

-- Character 테이블의 anime_id 컬럼을 NULL 허용으로 변경
ALTER TABLE characters ALTER COLUMN anime_id DROP NOT NULL;

-- VoiceActor 테이블의 anime_id 컬럼을 NULL 허용으로 변경  
ALTER TABLE voice_actors ALTER COLUMN anime_id DROP NOT NULL;
