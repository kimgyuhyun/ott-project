-- Weekly diversified seeds: distinct genres/tags/synopsis per title
-- Pre-requires base schema already created

-- 0) Extend master data (safe upsert)
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

-- Common column list
-- title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
-- status, release_date, end_date, age_rating, rating, rating_count,
-- is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
-- broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
-- voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at

-- 월요일 (Action/Fantasy/Comedy mix)
INSERT INTO anime (title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes, status, release_date, end_date, age_rating, rating, rating_count, is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast, broadcast_day, broad_cast_time, season, year, type, duration, source, country, language, voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at)
SELECT '월요신작 검과 마법의 서약', 'Monday Oath of Sword & Magic', '月曜新作 剣と魔法の誓い',
       '검과 마법으로 결속된 동료들의 여정이 시작된다.',
       '고대 서약을 계승한 주인공이 잃어버린 왕국의 비밀을 찾아 모험을 떠난다. 각성한 마법과 검술, 그리고 동료애가 성장의 핵심이다.',
       'https://cdn.example.com/posters/mon_oath.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '7 day', NULL, '15', 4.6, 228,
       false, true, true, false, true, true, true,
       '월', '20:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
       '성우A, 성우B', '감독A', 'Q3', 3, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '월요신작 검과 마법의 서약');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '월요신작 이세계 길드의 비밀', 'Monday Guild Secrets', '月曜新作 異世界ギルドの秘密',
       '이세계 길드의 내부에는 상상 못 할 규칙이 있다.',
       '초보 모험가가 길드의 금지된 규정을 알게 되며 세계에 얽힌 음모를 파헤치는 이야기. 규율과 우정, 그리고 선택의 무게를 그린다.',
       'https://cdn.example.com/posters/mon_guild.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '6 day', NULL, '12', 4.2, 163,
       false, true, false, false, true, true, true,
       '월', '20:30', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'LIGHT_NOVEL', 'JP', 'JA',
       '성우C, 성우D', '감독B', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '월요신작 이세계 길드의 비밀');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '월요신작 학원 만능 동아리', 'Monday All-Round Club', '月曜新作 学園万能部',
       '해결 못 하는 일이 없는 만능 동아리의 소동극.',
       '평범한 학생들이 모여 만든 동아리가 학원 내 사건·사고를 해결한다. 유쾌한 개그와 은근한 성장담이 포인트.',
       'https://cdn.example.com/posters/mon_club.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '5 day', NULL, '12', 3.9, 101,
       false, true, false, false, true, false, true,
       '월', '21:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
       '성우E, 성우F', '감독C', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '월요신작 학원 만능 동아리');

-- 화요일 (Romance/Slice/Mystery/Idol)
INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '화요신작 비 오는 날의 약속', 'Tuesday Promise in the Rain', '火曜新作 雨の日の約束',
       '비가 오면 만나는 두 사람의 감성 로맨스.',
       '우연이 반복되며 인연이 되는 과정. 빗소리와 함께 쌓여가는 마음을 잔잔하게 그린 힐링 로맨스.',
       'https://cdn.example.com/posters/tue_rain.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '7 day', NULL, '12', 4.3, 149,
       false, true, true, false, true, false, true,
       '화', '20:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
       '성우G, 성우H', '감독D', 'Q3', 3, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '화요신작 비 오는 날의 약속');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '화요신작 골목길의 그림자', 'Tuesday Shadows in Alley', '火曜新作 路地裏の影',
       '골목마다 남은 흔적을 좇는 청춘 미스터리.',
       '실종된 친구를 찾는 과정에서 드러나는 도시의 숨은 규칙들. 퍼즐처럼 이어지는 단서와 반전.',
       'https://cdn.example.com/posters/tue_shadow.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '6 day', NULL, '15', 4.4, 189,
       false, true, true, false, true, false, true,
       '화', '20:30', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
       '성우I, 성우J', '감독E', 'Q3', 3, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '화요신작 골목길의 그림자');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '화요신작 무대 뒤의 우리', 'Tuesday Backstage Us', '火曜新作 バックステージ・アス',
       '연습실에서 시작된 아이돌 꿈과 우정.',
       '무대 위 화려함 뒤의 땀과 눈물. 팀워크가 완성해가는 첫 데뷔 스토리.',
       'https://cdn.example.com/posters/tue_backstage.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '5 day', NULL, '12', 3.8, 86,
       false, true, false, false, true, false, true,
       '화', '21:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
       '성우K, 성우L', '감독F', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '화요신작 무대 뒤의 우리');

-- 수요일 (Sci-Fi/Mecha/Sports/Horror)
INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '수요신작 궤도의 파수꾼', 'Wednesday Orbital Sentinel', '水曜新作 軌道の番人',
       '우주 궤도를 지키는 소수 정예의 이야기.',
       '정체불명의 드리프트 현상에 맞서는 궤도 방위대. 과학과 신념이 충돌한다.',
       'https://cdn.example.com/posters/wed_orbital.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '7 day', NULL, '12', 4.6, 233,
       false, true, true, false, true, true, true,
       '수', '20:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
       '성우M, 성우N', '감독G', 'Q3', 4, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '수요신작 궤도의 파수꾼');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '수요신작 코트 위의 증명', 'Wednesday Proof on Court', '水曜新作 コートでの証明',
       '패배를 거름삼아 강해지는 스포츠 청춘.',
       '부상에서 복귀한 에이스와 신입들의 케미. 팀의 색을 찾아가는 성장기.',
       'https://cdn.example.com/posters/wed_court.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '6 day', NULL, '12', 4.0, 121,
       false, true, false, false, true, false, true,
       '수', '20:30', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
       '성우O, 성우P', '감독H', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '수요신작 코트 위의 증명');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '수요신작 밤의 초대장', 'Wednesday Invitation of Night', '水曜新作 夜への招待状',
       '사라진 초대장의 행방을 좇는 공포 추적기.',
       '초대장을 받은 사람은 하나둘 실종된다. 남겨진 흔적을 따라가면 마지막 페이지가 열린다.',
       'https://cdn.example.com/posters/wed_invite.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '5 day', NULL, '15', 3.7, 75,
       false, true, false, false, true, false, true,
       '수', '21:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
       '성우Q, 성우R', '감독I', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '수요신작 밤의 초대장');

-- 목요일 (Fantasy/Adventure/RomCom/Slice)
INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '목요신작 유적의 사서', 'Thursday Archivist of Ruins', '木曜新作 遺跡の司書',
       '유적을 기록하는 사서의 모험활극.',
       '폐허가 된 문명의 잔해에서 기록을 복원하는 임무. 봉인 해제와 각성의 순간들.',
       'https://cdn.example.com/posters/thu_archivist.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '7 day', NULL, '12', 4.3, 166,
       false, true, true, false, true, true, true,
       '목', '20:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'LIGHT_NOVEL', 'JP', 'JA',
       '성우S, 성우T', '감독J', 'Q3', 3, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '목요신작 유적의 사서');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '목요신작 옆집은 라이벌', 'Thursday Neighbors are Rivals', '木曜新作 隣人はライバル',
       '인생 최대의 옆집 로맨스 코미디.',
       '담장 하나를 사이에 둔 티키타카. 말로는 안 친한데 마음은 이미.',
       'https://cdn.example.com/posters/thu_neighbors.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '6 day', NULL, '12', 4.0, 114,
       false, true, false, false, true, false, true,
       '목', '20:30', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
       '성우U, 성우V', '감독K', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '목요신작 옆집은 라이벌');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '목요신작 티타임의 비밀', 'Thursday Secrets at Teatime', '木曜新作 ティータイムの秘密',
       '한 잔의 차에 얽힌 소소한 일상 추리.',
       '손님마다 다른 고민을 차와 함께 풀어가는 잔잔한 미스터리 슬라이스.',
       'https://cdn.example.com/posters/thu_teatime.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '5 day', NULL, 'ALL', 3.9, 92,
       false, true, false, false, true, false, true,
       '목', '21:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
       '성우W, 성우X', '감독L', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '목요신작 티타임의 비밀');

-- 금요일 (Adventure/Mystery/School)
INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '금요신작 바람의 정점', 'Friday Apex of Wind', '金曜新作 風の極点',
       '바람을 타는 모험가의 한계 돌파.',
       '대륙의 경계 너머로 불어오는 신풍을 좇아 떠나는 항해. 전설급 난이도에 도전한다.',
       'https://cdn.example.com/posters/fri_wind.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '7 day', NULL, '12', 4.7, 322,
       false, true, true, false, true, true, true,
       '금', '20:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
       '성우Y, 성우Z', '감독M', 'Q3', 4, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '금요신작 바람의 정점');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '금요신작 문이 열리는 밤', 'Friday When Doors Open', '金曜新作 扉の開く夜',
       '자정마다 나타나는 문 뒤의 진실.',
       '열두 개의 문이 가리키는 하나의 진실. 시간과 기억을 넘나드는 추적.',
       'https://cdn.example.com/posters/fri_doors.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '6 day', NULL, '15', 4.3, 182,
       false, true, true, false, true, false, true,
       '금', '20:30', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
       '성우AA, 성우AB', '감독N', 'Q3', 3, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '금요신작 문이 열리는 밤');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '금요신작 청춘의 교차점', 'Friday Crossroads of Youth', '金曜新作 青春の交差点',
       '선생님과 학생, 서로 배우는 한 학기.',
       '성적, 진로, 인간관계. 정답은 없지만 함께 답을 찾아간다.',
       'https://cdn.example.com/posters/fri_crossroads.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '5 day', NULL, '12', 3.9, 104,
       false, true, false, false, true, false, true,
       '금', '21:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
       '성우AC, 성우AD', '감독O', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '금요신작 청춘의 교차점');

-- 토요일 (Isekai/Healing/Sports)
INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '토요신작 포탈 너머의 정원', 'Saturday Garden Beyond Portal', '土曜新作 ポータルの庭',
       '포탈 너머에서 만난 새로운 규칙.',
       '세상 밖의 정원, 낯선 법칙 속에서 살아남는 이세계 생존기.',
       'https://cdn.example.com/posters/sat_portal.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '7 day', NULL, '12', 4.5, 242,
       false, true, true, false, true, true, true,
       '토', '20:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'LIGHT_NOVEL', 'JP', 'JA',
       '성우AE, 성우AF', '감독P', 'Q3', 4, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '토요신작 포탈 너머의 정원');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '토요신작 느린 골목 레시피', 'Saturday Slow Alley Recipe', '土曜新作 路地裏スローレシピ',
       '느리게 끓여 완성되는 마음 치유.',
       '작은 식당에서 만나는 사람 이야기. 한 접시에 담긴 위로.',
       'https://cdn.example.com/posters/sat_recipe.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '6 day', NULL, 'ALL', 4.0, 132,
       false, true, false, false, true, false, true,
       '토', '20:30', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
       '성우AG, 성우AH', '감독Q', 'Q3', 3, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '토요신작 느린 골목 레시피');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '토요신작 네 번째 쿼터', 'Saturday The Fourth Quarter', '土曜新作 第四クォーター',
       '마지막 5분, 포기하지 않는 코트의 근성.',
       '리그 잔류를 건 승부. 약팀의 반전 드라마.',
       'https://cdn.example.com/posters/sat_fourth.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '5 day', NULL, '12', 3.8, 96,
       false, true, false, false, true, false, true,
       '토', '21:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
       '성우AI, 성우AJ', '감독R', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '토요신작 네 번째 쿼터');

-- 일요일 (Fantasy/Romance/Mystery)
INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '일요신작 별빛에 맺은 서약', 'Sunday Vows under Starlight', '日曜新作 星明りの誓い',
       '별이 가장 밝은 밤, 선택의 시간.',
       '왕국을 구원할 별의 계약. 운명과 자유 사이의 결단.',
       'https://cdn.example.com/posters/sun_starlight.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '7 day', NULL, '12', 4.6, 257,
       false, true, true, false, true, true, true,
       '일', '20:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
       '성우AK, 성우AL', '감독S', 'Q3', 4, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '일요신작 별빛에 맺은 서약');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '일요신작 여름, 우리가 남긴 것', 'Sunday Summer We Left', '日曜新作 夏、僕らの残響',
       '여름의 끝에서 마주본 마음.',
       '잠깐 스쳐간 계절이 남긴 것들을 수습하는 성장 로맨스.',
       'https://cdn.example.com/posters/sun_summer.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '6 day', NULL, '12', 4.1, 152,
       false, true, false, false, true, false, true,
       '일', '20:30', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'ORIGINAL', 'JP', 'JA',
       '성우AM, 성우AN', '감독T', 'Q3', 3, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '일요신작 여름, 우리가 남긴 것');

INSERT INTO anime (
  title, title_en, title_jp, synopsis, full_synopsis, poster_url, total_episodes,
  status, release_date, end_date, age_rating, rating, rating_count,
  is_exclusive, is_new, is_popular, is_completed, is_subtitle, is_dub, is_simulcast,
  broadcast_day, broad_cast_time, season, year, type, duration, source, country, language,
  voice_actors, director, release_quarter, current_episodes, is_active, created_at, updated_at
)
SELECT '일요신작 마지막 단서', 'Sunday The Final Clue', '日曜新作 最後の手掛かり',
       '사건의 열쇠는 가장 가까운 곳에 있다.',
       '도시 전설의 실체를 추적하는 대학 탐사 서클의 기록.',
       'https://cdn.example.com/posters/sun_clue.jpg', 12,
       'ONGOING', CURRENT_DATE - INTERVAL '5 day', NULL, '15', 3.9, 111,
       false, true, false, false, true, false, true,
       '일', '21:00', '여름', EXTRACT(YEAR FROM CURRENT_DATE)::int, 'TV', 24, 'MANGA', 'JP', 'JA',
       '성우AO, 성우AP', '감독U', 'Q3', 2, true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM anime WHERE title = '일요신작 마지막 단서');

-- 2) Genre/Tag mappings (varied per title), dedupe-safe
-- 월
INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Action','Fantasy')
WHERE a.title = '월요신작 검과 마법의 서약'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('ADVENTURE','HOT','NEW')
WHERE a.title = '월요신작 검과 마법의 서약'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Fantasy','Adventure')
WHERE a.title = '월요신작 이세계 길드의 비밀'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('ISEKAI','ADVENTURE','NEW')
WHERE a.title = '월요신작 이세계 길드의 비밀'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Comedy','Slice of Life')
WHERE a.title = '월요신작 학원 만능 동아리'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('SCHOOL','TRENDING')
WHERE a.title = '월요신작 학원 만능 동아리'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

-- 화
INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Romance','Slice of Life')
WHERE a.title = '화요신작 비 오는 날의 약속'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('HEALING','NEW')
WHERE a.title = '화요신작 비 오는 날의 약속'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Mystery','Horror')
WHERE a.title = '화요신작 골목길의 그림자'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('TRENDING','NEW')
WHERE a.title = '화요신작 골목길의 그림자'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Comedy','Slice of Life')
WHERE a.title = '화요신작 무대 뒤의 우리'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('IDOL','TRENDING')
WHERE a.title = '화요신작 무대 뒤의 우리'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

-- 수
INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Sci-Fi','Action')
WHERE a.title = '수요신작 궤도의 파수꾼'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('MECHA','ADVENTURE','HOT')
WHERE a.title = '수요신작 궤도의 파수꾼'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Sports','Slice of Life')
WHERE a.title = '수요신작 코트 위의 증명'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('TRENDING')
WHERE a.title = '수요신작 코트 위의 증명'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Horror','Mystery')
WHERE a.title = '수요신작 밤의 초대장'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('TRENDING')
WHERE a.title = '수요신작 밤의 초대장'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

-- 목
INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Fantasy','Adventure')
WHERE a.title = '목요신작 유적의 사서'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('ADVENTURE','NEW')
WHERE a.title = '목요신작 유적의 사서'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Romance','Comedy')
WHERE a.title = '목요신작 옆집은 라이벌'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('SCHOOL','TRENDING')
WHERE a.title = '목요신작 옆집은 라이벌'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Slice of Life','Mystery')
WHERE a.title = '목요신작 티타임의 비밀'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('HEALING')
WHERE a.title = '목요신작 티타임의 비밀'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

-- 금
INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Adventure','Action')
WHERE a.title = '금요신작 바람의 정점'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('ADVENTURE','HOT','TRENDING')
WHERE a.title = '금요신작 바람의 정점'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Mystery','Horror')
WHERE a.title = '금요신작 문이 열리는 밤'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('TRENDING','NEW')
WHERE a.title = '금요신작 문이 열리는 밤'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Sports','Slice of Life')
WHERE a.title = '금요신작 청춘의 교차점'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('SCHOOL')
WHERE a.title = '금요신작 청춘의 교차점'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

-- 토
INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Fantasy','Adventure')
WHERE a.title = '토요신작 포탈 너머의 정원'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('ISEKAI','ADVENTURE','TRENDING')
WHERE a.title = '토요신작 포탈 너머의 정원'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Slice of Life','Comedy')
WHERE a.title = '토요신작 느린 골목 레시피'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('HEALING')
WHERE a.title = '토요신작 느린 골목 레시피'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Sports','Slice of Life')
WHERE a.title = '토요신작 네 번째 쿼터'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('TRENDING')
WHERE a.title = '토요신작 네 번째 쿼터'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

-- 일
INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Fantasy','Adventure')
WHERE a.title = '일요신작 별빛에 맺은 서약'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('ADVENTURE','HOT','NEW')
WHERE a.title = '일요신작 별빛에 맺은 서약'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Romance','Slice of Life')
WHERE a.title = '일요신작 여름, 우리가 남긴 것'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('HEALING','TRENDING')
WHERE a.title = '일요신작 여름, 우리가 남긴 것'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);

INSERT INTO anime_genres (anime_id, genre_id)
SELECT a.id, g.id FROM anime a JOIN genres g ON g.name IN ('Mystery','Horror')
WHERE a.title = '일요신작 마지막 단서'
AND NOT EXISTS (SELECT 1 FROM anime_genres ag WHERE ag.anime_id=a.id AND ag.genre_id=g.id);
INSERT INTO anime_tags (anime_id, tag_id)
SELECT a.id, t.id FROM anime a JOIN tags t ON t.name IN ('TRENDING','NEW')
WHERE a.title = '일요신작 마지막 단서'
AND NOT EXISTS (SELECT 1 FROM anime_tags at WHERE at.anime_id=a.id AND at.tag_id=t.id);
