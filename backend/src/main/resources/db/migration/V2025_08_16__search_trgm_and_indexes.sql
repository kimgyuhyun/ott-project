-- 트라이그램 확장 및 검색 최적화 인덱스 생성

-- 확장: pg_trgm (부분일치/유사도 검색용)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 제목 컬럼 트라이그램 인덱스 (부분일치 ILIKE 최적화)
CREATE INDEX IF NOT EXISTS idx_anime_title_trgm
  ON anime USING gin (title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_anime_title_en_trgm
  ON anime USING gin (title_en gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_anime_title_jp_trgm
  ON anime USING gin (title_jp gin_trgm_ops);

-- 장르 AND 매칭 및 태그 OR 매칭 최적화용 조인 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_anime_genres_genre_id_anime_id
  ON anime_genres (genre_id, anime_id);

CREATE INDEX IF NOT EXISTS idx_anime_tags_tag_id_anime_id
  ON anime_tags (tag_id, anime_id);


