-- 성능 최적화를 위한 인덱스 추가
-- 캐릭터, 성우, 감독, 스튜디오, 장르, 태그 테이블의 name 컬럼에 인덱스 추가

-- 캐릭터 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_characters_name ON characters(name);

-- 성우 테이블 인덱스  
CREATE INDEX IF NOT EXISTS idx_voice_actors_name ON voice_actors(name);

-- 감독 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_directors_name ON directors(name);

-- 스튜디오 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_studios_name ON studios(name);

-- 장르 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_genres_name ON genres(name);

-- 태그 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_tags_name ON tags(name);
