-- Seed minimal demo data for local testing (genres, tags, studios, anime, episodes, membership plans)
-- Assumes PostgreSQL and existing schema from JPA/Hibernate naming strategy (snake_case)

-- 1) Master tables: genres, tags, studios
INSERT INTO genres (name, description, color, is_active, created_at, updated_at)
VALUES
  ('Action', '액션 장르', '#ff4757', true, now(), now()),
  ('Fantasy', '판타지 장르', '#5352ed', true, now(), now()),
  ('Comedy', '코미디 장르', '#ffa502', true, now(), now())
ON CONFLICT (name) DO NOTHING;

INSERT INTO tags (name, color)
VALUES
  ('HOT', '#e84118'),
  ('TRENDING', '#0097e6'),
  ('NEW', '#44bd32')
ON CONFLICT (name) DO NOTHING;

INSERT INTO studios (name, name_en, name_jp, description, logo_url, website_url, country, is_active, created_at, updated_at)
VALUES
  ('MAPPA', 'MAPPA', 'マッパ', '유명 제작사', null, 'https://mappa.co.jp', 'JP', true, now(), now()),
  ('Ufotable', 'Ufotable', 'ユーフォーテーブル', '애니메이션 제작사', null, 'https://www.ufotable.com/', 'JP', true, now(), now())
ON CONFLICT (name) DO NOTHING;

-- 2) Membership plans
INSERT INTO plans (code, name, max_quality, price_monthly_vat_included, price_currency, period_months, concurrent_streams)
VALUES
  ('BASIC_MONTHLY', 'Basic Monthly', '1080p', 9900, 'KRW', 1, 1),
  ('PREMIUM_MONTHLY', 'Premium Monthly', '4K', 14900, 'KRW', 1, 4)
ON CONFLICT (code) DO NOTHING;

-- 3) One demo anime (insert if not exists)
INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '데모 애니메이션', 'Demo Animation', 'デモアニメ',
  '데모용 간단 줄거리',
  '데모용 상세 줄거리입니다. 더미 데이터로 시청/검색/리뷰 테스트가 가능합니다.',
  'https://cdn.example.com/posters/demo.jpg',
  12,
  'ONGOING',
  CURRENT_DATE - INTERVAL '30 day',
  null,
  '15',
  4.3,
  127,
  true,  true,  true,  false,
  true,  true,  false,
  '금', '21:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24,
  'MANGA', 'JP', 'JA',
  '성우 A, 성우 B, 성우 C',
  '감독 X',
  'Q3',
  3,
  true,
  now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '데모 애니메이션');

-- Relations: genres, tags, studios (avoid duplicates)
INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Action','Fantasy')
LEFT JOIN anime_genres ag ON ag.anime_id = a.id AND ag.genre_id = g.id
WHERE a.title = '데모 애니메이션' AND ag.anime_id IS NULL;

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('HOT','NEW')
LEFT JOIN anime_tags at ON at.anime_id = a.id AND at.tag_id = t.id
WHERE a.title = '데모 애니메이션' AND at.anime_id IS NULL;

INSERT INTO anime_studios (anime_id, studio_id)
SELECT a.id, s.id
FROM anime a
JOIN studios s ON s.name IN ('MAPPA')
LEFT JOIN anime_studios ast ON ast.anime_id = a.id AND ast.studio_id = s.id
WHERE a.title = '데모 애니메이션' AND ast.anime_id IS NULL;

-- 4) Episodes 1..5 for the demo anime
WITH ar AS (
  SELECT id FROM anime WHERE title = '데모 애니메이션'
)
INSERT INTO episodes (
  episode_number, title, thumbnail_url, video_url, is_active, is_released, anime_id, created_at, updated_at
) 
SELECT 
  gs AS episode_number,
  '데모 에피소드 ' || gs,
  'https://cdn.example.com/thumbs/demo_' || gs || '.jpg',
  'https://cdn.example.com/hls/demo/master.m3u8',
  true, true,
  ar.id,
  now(), now()
FROM ar CROSS JOIN generate_series(1,5) AS gs
WHERE NOT EXISTS (
  SELECT 1 FROM episodes e WHERE e.anime_id = ar.id AND e.episode_number = gs
);


