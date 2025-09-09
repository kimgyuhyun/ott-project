-- Character와 VoiceActor 테이블 정리 및 anime_id NOT NULL 제약조건 유지
-- 기존 데이터를 삭제하고 올바른 스키마로 재구성

-- 1. 기존 데이터 삭제 (anime_id가 NULL인 데이터들)
DELETE FROM characters WHERE anime_id IS NULL;
DELETE FROM voice_actors WHERE anime_id IS NULL;

-- 2. anime_id 컬럼을 NOT NULL로 강제 설정
ALTER TABLE characters ALTER COLUMN anime_id SET NOT NULL;
ALTER TABLE voice_actors ALTER COLUMN anime_id SET NOT NULL;

-- 3. 외래키 제약조건 추가 (이미 존재한다면 무시됨)
DO $$
BEGIN
    -- characters 테이블 외래키 제약조건 추가
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_characters_anime_id' 
        AND table_name = 'characters'
    ) THEN
        ALTER TABLE characters ADD CONSTRAINT fk_characters_anime_id 
            FOREIGN KEY (anime_id) REFERENCES anime(id) ON DELETE CASCADE;
    END IF;
    
    -- voice_actors 테이블 외래키 제약조건 추가
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_voice_actors_anime_id' 
        AND table_name = 'voice_actors'
    ) THEN
        ALTER TABLE voice_actors ADD CONSTRAINT fk_voice_actors_anime_id 
            FOREIGN KEY (anime_id) REFERENCES anime(id) ON DELETE CASCADE;
    END IF;
END $$;

-- 4. 인덱스 추가 (성능 최적화)
CREATE INDEX IF NOT EXISTS idx_characters_anime_id ON characters(anime_id);
CREATE INDEX IF NOT EXISTS idx_voice_actors_anime_id ON voice_actors(anime_id);
