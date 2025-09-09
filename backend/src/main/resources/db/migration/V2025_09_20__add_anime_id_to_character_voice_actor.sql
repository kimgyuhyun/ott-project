-- Character와 VoiceActor 테이블에 animeId 컬럼 추가
-- 이 컬럼은 내 DB의 애니메이션 PK를 저장하여 "이 캐릭터/성우가 어느 애니메이션에 속하는지" 알 수 있게 함

-- Character 테이블에 animeId 컬럼 추가
ALTER TABLE characters ADD COLUMN anime_id BIGINT NOT NULL;

-- VoiceActor 테이블에 animeId 컬럼 추가  
ALTER TABLE voice_actors ADD COLUMN anime_id BIGINT NOT NULL;

-- 외래키 제약조건 추가
ALTER TABLE characters ADD CONSTRAINT fk_characters_anime_id 
    FOREIGN KEY (anime_id) REFERENCES anime(id);

ALTER TABLE voice_actors ADD CONSTRAINT fk_voice_actors_anime_id 
    FOREIGN KEY (anime_id) REFERENCES anime(id);

-- 인덱스 추가 (성능 최적화)
CREATE INDEX idx_characters_anime_id ON characters(anime_id);
CREATE INDEX idx_voice_actors_anime_id ON voice_actors(anime_id);
