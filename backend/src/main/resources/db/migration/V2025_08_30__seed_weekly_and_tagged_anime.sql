-- Seed weekly schedule (Mon~Sun, 3 each) + genre/tag masters and mappings
-- Schema references:
-- - anime(title unique, status: ONGOING/COMPLETED/UPCOMING/HIATUS)
-- - anime_genres(anime_id, genre_id)
-- - anime_tags(anime_id, tag_id)
-- - broadcast_day: '월','화','수','목','금','토','일'
-- - broad_cast_time: 'HH:mm' string

-- 0) Ensure master data (genres/tags)
INSERT INTO genres (name, description, color, is_active, created_at, updated_at)
VALUES
  ('Romance', '로맨스 장르', '#e84393', true, now(), now()),
  ('Sci-Fi', '공상과학 장르', '#00cec9', true, now(), now()),
  ('Slice of Life', '일상물 장르', '#81ecec', true, now(), now()),
  ('Mystery', '미스터리 장르', '#6c5ce7', true, now(), now()),
  ('Sports', '스포츠 장르', '#00b894', true, now(), now()),
  ('Horror', '호러 장르', '#2d3436', true, now(), now())
ON CONFLICT (name) DO NOTHING;

INSERT INTO tags (name, color)
VALUES
  ('ISEKAI', '#8e44ad'),
  ('SCHOOL', '#0984e3'),
  ('IDOL', '#fd79a8'),
  ('MECHA', '#636e72'),
  ('HEALING', '#55efc4'),
  ('ADVENTURE', '#e17055')
ON CONFLICT (name) DO NOTHING;

-- 1) Seed 21 animes: 3 per weekday (월~일)
-- Required cols: title, poster_url, total_episodes, status, release_date, age_rating, rating, rating_count,
-- is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
-- broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
-- current_episodes, is_active, created_at, updated_at
-- Optional cols given as needed: title_en, title_jp, synopsis, full_synopsis, end_date, voice_actors, director, release_quarter

-- 월요일
INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '월요신작 액션 판타지 1', 'Monday Action Fantasy 1', '月曜新作 アクション ファンタジー 1',
  '월요일 신작 액션 판타지', '월요일 신작 액션 판타지 상세 줄거리', 'https://cdn.example.com/posters/mon_af_1.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '7 day', NULL, '15', 4.5, 210,
  false, true, true, false, true, true, true,
  '월', '20:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
  '성우A, 성우B', '감독A', 'Q3', 3, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '월요신작 액션 판타지 1');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '월요신작 이세계 어드벤처 2', 'Monday Isekai Adventure 2', '月曜新作 異世界 アドベンチャー 2',
  '월요일 신작 이세계 어드벤처', '월요일 신작 이세계 어드벤처 상세', 'https://cdn.example.com/posters/mon_ia_2.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '6 day', NULL, '12', 4.1, 156,
  false, true, false, false, true, true, true,
  '월', '20:30', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'LIGHT_NOVEL', 'JP', 'JA',
  '성우C, 성우D', '감독B', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '월요신작 이세계 어드벤처 2');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '월요신작 학원 코미디 3', 'Monday School Comedy 3', '月曜新作 学園 コメディ 3',
  '월요일 신작 학원 코미디', '월요일 신작 학원 코미디 상세', 'https://cdn.example.com/posters/mon_sc_3.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '5 day', NULL, '12', 3.9, 98,
  false, true, false, false, true, false, true,
  '월', '21:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
  '성우E, 성우F', '감독C', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '월요신작 학원 코미디 3');

-- 화요일
INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '화요신작 로맨스 일상 1', 'Tuesday Romance Slice 1', '火曜新作 ロマンス 日常 1',
  '화요일 신작 로맨스 일상', '화요일 신작 로맨스 일상 상세', 'https://cdn.example.com/posters/tue_rs_1.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '7 day', NULL, '12', 4.2, 143,
  false, true, true, false, true, false, true,
  '화', '20:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
  '성우G, 성우H', '감독D', 'Q3', 3, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '화요신작 로맨스 일상 1');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '화요신작 미스터리 스릴 2', 'Tuesday Mystery Thrill 2', '火曜新作 ミステリー スリル 2',
  '화요일 신작 미스터리', '화요일 신작 미스터리 상세', 'https://cdn.example.com/posters/tue_mt_2.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '6 day', NULL, '15', 4.4, 187,
  false, true, true, false, true, false, true,
  '화', '20:30', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
  '성우I, 성우J', '감독E', 'Q3', 3, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '화요신작 미스터리 스릴 2');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '화요신작 아이돌 음악 3', 'Tuesday Idol Music 3', '火曜新作 アイドル 音楽 3',
  '화요일 신작 아이돌 음악', '화요일 신작 아이돌 음악 상세', 'https://cdn.example.com/posters/tue_im_3.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '5 day', NULL, '12', 3.8, 85,
  false, true, false, false, true, false, true,
  '화', '21:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
  '성우K, 성우L', '감독F', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '화요신작 아이돌 음악 3');

-- 수요일
INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '수요신작 공상과학 메카 1', 'Wednesday Sci-Fi Mecha 1', '水曜新作 SF メカ 1',
  '수요일 신작 공상과학 메카', '수요일 신작 공상과학 메카 상세', 'https://cdn.example.com/posters/wed_sm_1.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '7 day', NULL, '12', 4.6, 230,
  false, true, true, false, true, true, true,
  '수', '20:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
  '성우M, 성우N', '감독G', 'Q3', 4, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '수요신작 공상과학 메카 1');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '수요신작 스포츠 청춘 2', 'Wednesday Sports Youth 2', '水曜新作 スポーツ 青春 2',
  '수요일 신작 스포츠', '수요일 신작 스포츠 상세', 'https://cdn.example.com/posters/wed_sy_2.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '6 day', NULL, '12', 4.0, 120,
  false, true, false, false, true, false, true,
  '수', '20:30', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
  '성우O, 성우P', '감독H', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '수요신작 스포츠 청춘 2');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '수요신작 호러 미스터리 3', 'Wednesday Horror Mystery 3', '水曜新作 ホラー ミステリー 3',
  '수요일 신작 호러 미스터리', '수요일 신작 호러 미스터리 상세', 'https://cdn.example.com/posters/wed_hm_3.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '5 day', NULL, '15', 3.7, 74,
  false, true, false, false, true, false, true,
  '수', '21:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
  '성우Q, 성우R', '감독I', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '수요신작 호러 미스터리 3');

-- 목요일
INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '목요신작 판타지 어드벤처 1', 'Thursday Fantasy Adventure 1', '木曜新作 ファンタジー アドベンチャー 1',
  '목요일 신작 판타지 어드벤처', '목요일 신작 판타지 어드벤처 상세', 'https://cdn.example.com/posters/thu_fa_1.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '7 day', NULL, '12', 4.3, 162,
  false, true, true, false, true, true, true,
  '목', '20:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'LIGHT_NOVEL', 'JP', 'JA',
  '성우S, 성우T', '감독J', 'Q3', 3, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '목요신작 판타지 어드벤처 1');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '목요신작 로맨스 코미디 2', 'Thursday RomCom 2', '木曜新作 ロマンス コメディ 2',
  '목요일 신작 로맨스 코미디', '목요일 신작 로맨스 코미디 상세', 'https://cdn.example.com/posters/thu_rc_2.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '6 day', NULL, '12', 4.0, 112,
  false, true, false, false, true, false, true,
  '목', '20:30', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
  '성우U, 성우V', '감독K', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '목요신작 로맨스 코미디 2');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '목요신작 일상 힐링 3', 'Thursday Slice Healing 3', '木曜新作 日常 ヒーリング 3',
  '목요일 신작 일상 힐링', '목요일 신작 일상 힐링 상세', 'https://cdn.example.com/posters/thu_sh_3.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '5 day', NULL, 'ALL', 3.9, 90,
  false, true, false, false, true, false, true,
  '목', '21:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
  '성우W, 성우X', '감독L', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '목요신작 일상 힐링 3');

-- 금요일
INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '금요신작 인기 어드벤처 1', 'Friday Popular Adventure 1', '金曜新作 人気 アドベンチャー 1',
  '금요일 신작 인기 어드벤처', '금요일 신작 인기 어드벤처 상세', 'https://cdn.example.com/posters/fri_pa_1.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '7 day', NULL, '12', 4.7, 320,
  false, true, true, false, true, true, true,
  '금', '20:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
  '성우Y, 성우Z', '감독M', 'Q3', 4, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '금요신작 인기 어드벤처 1');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '금요신작 미스터리 스릴 2', 'Friday Mystery Thrill 2', '金曜新作 ミステリー スリル 2',
  '금요일 신작 미스터리', '금요일 신작 미스터리 상세', 'https://cdn.example.com/posters/fri_mt_2.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '6 day', NULL, '15', 4.3, 180,
  false, true, true, false, true, false, true,
  '금', '20:30', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
  '성우AA, 성우AB', '감독N', 'Q3', 3, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '금요신작 미스터리 스릴 2');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '금요신작 학원 청춘 3', 'Friday School Youth 3', '金曜新作 学園 青春 3',
  '금요일 신작 학원 청춘', '금요일 신작 학원 청춘 상세', 'https://cdn.example.com/posters/fri_sy_3.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '5 day', NULL, '12', 3.9, 102,
  false, true, false, false, true, false, true,
  '금', '21:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
  '성우AC, 성우AD', '감독O', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '금요신작 학원 청춘 3');

-- 토요일
INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '토요신작 이세계 모험 1', 'Saturday Isekai Quest 1', '土曜新作 異世界 クエスト 1',
  '토요일 신작 이세계 모험', '토요일 신작 이세계 모험 상세', 'https://cdn.example.com/posters/sat_iq_1.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '7 day', NULL, '12', 4.5, 240,
  false, true, true, false, true, true, true,
  '토', '20:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'LIGHT_NOVEL', 'JP', 'JA',
  '성우AE, 성우AF', '감독P', 'Q3', 4, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '토요신작 이세계 모험 1');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '토요신작 일상 힐링 2', 'Saturday Slice Healing 2', '土曜新作 日常 ヒーリング 2',
  '토요일 신작 일상 힐링', '토요일 신작 일상 힐링 상세', 'https://cdn.example.com/posters/sat_sh_2.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '6 day', NULL, 'ALL', 4.0, 130,
  false, true, false, false, true, false, true,
  '토', '20:30', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
  '성우AG, 성우AH', '감독Q', 'Q3', 3, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '토요신작 일상 힐링 2');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '토요신작 스포츠 리그 3', 'Saturday Sports League 3', '土曜新作 スポーツ リーグ 3',
  '토요일 신작 스포츠', '토요일 신작 스포츠 상세', 'https://cdn.example.com/posters/sat_sl_3.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '5 day', NULL, '12', 3.8, 95,
  false, true, false, false, true, false, true,
  '토', '21:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
  '성우AI, 성우AJ', '감독R', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '토요신작 스포츠 리그 3');

-- 일요일
INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '일요신작 판타지 대서사 1', 'Sunday Fantasy Epic 1', '日曜新作 ファンタジー 叙事詩 1',
  '일요일 신작 판타지', '일요일 신작 판타지 상세', 'https://cdn.example.com/posters/sun_fe_1.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '7 day', NULL, '12', 4.6, 255,
  false, true, true, false, true, true, true,
  '일', '20:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
  '성우AK, 성우AL', '감독S', 'Q3', 4, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '일요신작 판타지 대서사 1');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '일요신작 로맨스 드라마 2', 'Sunday Romance Drama 2', '日曜新作 ロマンス ドラマ 2',
  '일요일 신작 로맨스', '일요일 신작 로맨스 상세', 'https://cdn.example.com/posters/sun_rd_2.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '6 day', NULL, '12', 4.1, 150,
  false, true, false, false, true, false, true,
  '일', '20:30', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
  '성우AM, 성우AN', '감독T', 'Q3', 3, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '일요신작 로맨스 드라마 2');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT
  '일요신작 미스터리 추적 3', 'Sunday Mystery Chase 3', '日曜新作 ミステリー 追跡 3',
  '일요일 신작 미스터리', '일요일 신작 미스터리 상세', 'https://cdn.example.com/posters/sun_mc_3.jpg', 12,
  'ONGOING', CURRENT_DATE - INTERVAL '5 day', NULL, '15', 3.9, 110,
  false, true, false, false, true, false, true,
  '일', '21:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
  '성우AO, 성우AP', '감독U', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '일요신작 미스터리 추적 3');

-- 2) Map genres and tags (grouped by titles), avoid duplicates

-- 월요일 매핑
INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Action','Fantasy')
WHERE a.title IN ('월요신작 액션 판타지 1')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('ADVENTURE','HOT','NEW')
WHERE a.title IN ('월요신작 액션 판타지 1')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Fantasy','Action')
WHERE a.title IN ('월요신작 이세계 어드벤처 2')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('ISEKAI','ADVENTURE','NEW')
WHERE a.title IN ('월요신작 이세계 어드벤처 2')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Comedy','Slice of Life')
WHERE a.title IN ('월요신작 학원 코미디 3')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('SCHOOL','TRENDING')
WHERE a.title IN ('월요신작 학원 코미디 3')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

-- 화요일 매핑
INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Romance','Slice of Life')
WHERE a.title IN ('화요신작 로맨스 일상 1')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('HEALING','NEW')
WHERE a.title IN ('화요신작 로맨스 일상 1')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Mystery','Horror')
WHERE a.title IN ('화요신작 미스터리 스릴 2')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('TRENDING','NEW')
WHERE a.title IN ('화요신작 미스터리 스릴 2')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Comedy','Slice of Life')
WHERE a.title IN ('화요신작 아이돌 음악 3')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('IDOL','TRENDING')
WHERE a.title IN ('화요신작 아이돌 음악 3')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

-- 수요일 매핑
INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Sci-Fi','Action')
WHERE a.title IN ('수요신작 공상과학 메카 1')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('MECHA','ADVENTURE','HOT')
WHERE a.title IN ('수요신작 공상과학 메카 1')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Sports','Slice of Life')
WHERE a.title IN ('수요신작 스포츠 청춘 2')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('TRENDING')
WHERE a.title IN ('수요신작 스포츠 청춘 2')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Horror','Mystery')
WHERE a.title IN ('수요신작 호러 미스터리 3')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('TRENDING')
WHERE a.title IN ('수요신작 호러 미스터리 3')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

-- 목요일 매핑
INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Fantasy','Adventure')
WHERE a.title IN ('목요신작 판타지 어드벤처 1')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('ADVENTURE','NEW')
WHERE a.title IN ('목요신작 판타지 어드벤처 1')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Romance','Comedy')
WHERE a.title IN ('목요신작 로맨스 코미디 2')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('SCHOOL','TRENDING')
WHERE a.title IN ('목요신작 로맨스 코미디 2')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Slice of Life','Comedy')
WHERE a.title IN ('목요신작 일상 힐링 3')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('HEALING')
WHERE a.title IN ('목요신작 일상 힐링 3')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

-- 금요일 매핑
INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Adventure','Action')
WHERE a.title IN ('금요신작 인기 어드벤처 1')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('ADVENTURE','HOT','TRENDING')
WHERE a.title IN ('금요신작 인기 어드벤처 1')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Mystery','Horror')
WHERE a.title IN ('금요신작 미스터리 스릴 2')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('TRENDING','NEW')
WHERE a.title IN ('금요신작 미스터리 스릴 2')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Sports','Slice of Life')
WHERE a.title IN ('금요신작 학원 청춘 3')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('SCHOOL')
WHERE a.title IN ('금요신작 학원 청춘 3')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

-- 토요일 매핑
INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Fantasy','Adventure')
WHERE a.title IN ('토요신작 이세계 모험 1')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('ISEKAI','ADVENTURE','TRENDING')
WHERE a.title IN ('토요신작 이세계 모험 1')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Slice of Life','Comedy')
WHERE a.title IN ('토요신작 일상 힐링 2')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('HEALING')
WHERE a.title IN ('토요신작 일상 힐링 2')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Sports','Slice of Life')
WHERE a.title IN ('토요신작 스포츠 리그 3')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('TRENDING')
WHERE a.title IN ('토요신작 스포츠 리그 3')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

-- 일요일 매핑
INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Fantasy','Adventure')
WHERE a.title IN ('일요신작 판타지 대서사 1')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('ADVENTURE','HOT','NEW')
WHERE a.title IN ('일요신작 판타지 대서사 1')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Romance','Slice of Life')
WHERE a.title IN ('일요신작 로맨스 드라마 2')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('HEALING','TRENDING')
WHERE a.title IN ('일요신작 로맨스 드라마 2')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id
FROM anime a
JOIN genres g ON g.name IN ('Mystery','Horror')
WHERE a.title IN ('일요신작 미스터리 추적 3')
  AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id = a.id AND ag.genre_id = g.id);

INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id
FROM anime a
JOIN tags t ON t.name IN ('TRENDING','NEW')
WHERE a.title IN ('일요신작 미스터리 추적 3')
  AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id = a.id AND at.tag_id = t.id);
