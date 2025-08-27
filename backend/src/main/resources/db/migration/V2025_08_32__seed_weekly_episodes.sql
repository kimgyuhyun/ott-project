-- Seed episodes (3 per anime) for weekly seeded titles
-- Assumes all below titles already exist in anime table
-- episodes: (id, episode_number, title, thumbnail_url, video_url, is_active, is_released, anime_id, created_at, updated_at)
-- UNIQUE(anime_id, episode_number)

-- 1단계: 각 작품당 1~3화 삽입 (중복 방지)
INSERT INTO episodes (
  episode_number,
  title,
  thumbnail_url,
  video_url,
  is_active,
  is_released,
  anime_id,
  created_at,
  updated_at
)
SELECT
  gs AS episode_number,
  a.title || ' ' || gs || '화' AS title,
  'https://cdn.example.com/thumbs/a' || a.id || '_' || gs || '.jpg' AS thumbnail_url,
  'https://cdn.example.com/hls/a' || a.id || '/master.m3u8' AS video_url,
  TRUE AS is_active,
  TRUE AS is_released,
  a.id AS anime_id,
  now(), now()
FROM anime a
CROSS JOIN generate_series(1, 3) AS gs
WHERE a.title IN (
  '월요신작 검과 마법의 서약',
  '월요신작 이세계 길드의 비밀',
  '월요신작 학원 만능 동아리',
  '화요신작 비 오는 날의 약속',
  '화요신작 골목길의 그림자',
  '화요신작 무대 뒤의 우리',
  '수요신작 궤도의 파수꾼',
  '수요신작 코트 위의 증명',
  '수요신작 밤의 초대장',
  '목요신작 유적의 사서',
  '목요신작 옆집은 라이벌',
  '목요신작 티타임의 비밀',
  '금요신작 바람의 정점',
  '금요신작 문이 열리는 밤',
  '금요신작 청춘의 교차점',
  '토요신작 포탈 너머의 정원',
  '토요신작 느린 골목 레시피',
  '토요신작 네 번째 쿼터',
  '일요신작 별빛에 맺은 서약',
  '일요신작 여름, 우리가 남긴 것',
  '일요신작 마지막 단서'
)
AND NOT EXISTS (
  SELECT 1
  FROM episodes e
  WHERE e.anime_id = a.id
    AND e.episode_number = gs
);

-- 2단계: current_episodes 보정: 이미 더 큰 값이 있으면 유지
UPDATE anime a
SET current_episodes = GREATEST(
  a.current_episodes,
  COALESCE(sub.cnt, 0)
)
FROM (
  SELECT anime_id, COUNT(*) AS cnt
  FROM episodes
  WHERE anime_id IN (
    SELECT id FROM anime 
    WHERE title IN (
      '월요신작 검과 마법의 서약',
      '월요신작 이세계 길드의 비밀',
      '월요신작 학원 만능 동아리',
      '화요신작 비 오는 날의 약속',
      '화요신작 골목길의 그림자',
      '화요신작 무대 뒤의 우리',
      '수요신작 궤도의 파수꾼',
      '수요신작 코트 위의 증명',
      '수요신작 밤의 초대장',
      '목요신작 유적의 사서',
      '목요신작 옆집은 라이벌',
      '목요신작 티타임의 비밀',
      '금요신작 바람의 정점',
      '금요신작 문이 열리는 밤',
      '금요신작 청춘의 교차점',
      '토요신작 포탈 너머의 정원',
      '토요신작 느린 골목 레시피',
      '토요신작 네 번째 쿼터',
      '일요신작 별빛에 맺은 서약',
      '일요신작 여름, 우리가 남긴 것',
      '일요신작 마지막 단서'
    )
  )
  GROUP BY anime_id
) sub
WHERE a.id = sub.anime_id;