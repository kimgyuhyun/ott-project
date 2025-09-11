-- 애니 목록 커서/정렬 최적화 인덱스 (PostgreSQL/MySQL 8+ 호환 형식)

-- 최신순(id DESC)
CREATE INDEX IF NOT EXISTS idx_anime_active_id_desc
  ON anime (is_active, id DESC);

-- 평점순(rating DESC, id DESC)
CREATE INDEX IF NOT EXISTS idx_anime_active_rating_desc_id_desc
  ON anime (is_active, rating DESC, id DESC);

-- 인기순(is_popular DESC, id DESC)
CREATE INDEX IF NOT EXISTS idx_anime_active_popular_desc_id_desc
  ON anime (is_active, is_popular DESC, id DESC);

-- 장르/태그 조인 최적화
CREATE INDEX IF NOT EXISTS idx_anime_genres_anime_genre
  ON anime_genres (anime_id, genre_id);

CREATE INDEX IF NOT EXISTS idx_anime_tags_anime_tag
  ON anime_tags (anime_id, tag_id);

-- PostgreSQL: 인덱스 적용 직후 통계 갱신
ANALYZE anime;
ANALYZE anime_genres;
ANALYZE anime_tags;


