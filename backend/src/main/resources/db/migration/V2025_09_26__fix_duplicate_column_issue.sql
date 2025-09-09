-- 중복 컬럼 문제 해결
-- mal_character_id와 mal_person_id 컬럼이 이미 존재하는 경우를 처리

-- 기존 컬럼이 존재하는지 확인하고 없으면 추가
DO $$ 
BEGIN
    -- anime_characters 테이블에 mal_character_id 컬럼이 없으면 추가
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'anime_characters' 
                   AND column_name = 'mal_character_id') THEN
        ALTER TABLE anime_characters ADD COLUMN mal_character_id BIGINT;
    END IF;
    
    -- character_voice_actors 테이블에 mal_person_id 컬럼이 없으면 추가
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'character_voice_actors' 
                   AND column_name = 'mal_person_id') THEN
        ALTER TABLE character_voice_actors ADD COLUMN mal_person_id BIGINT;
    END IF;
END $$;

-- 인덱스가 없으면 추가
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_anime_characters_mal_character_id') THEN
        CREATE INDEX idx_anime_characters_mal_character_id ON anime_characters(mal_character_id);
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_character_voice_actors_mal_person_id') THEN
        CREATE INDEX idx_character_voice_actors_mal_person_id ON character_voice_actors(mal_person_id);
    END IF;
END $$;

-- 코멘트 추가 (이미 있으면 무시됨)
COMMENT ON COLUMN anime_characters.mal_character_id IS 'MyAnimeList 캐릭터 고유 ID (해당 애니메이션에서의 캐릭터)';
COMMENT ON COLUMN character_voice_actors.mal_person_id IS 'MyAnimeList 성우(퍼슨) 고유 ID (해당 캐릭터를 연기한 성우)';
