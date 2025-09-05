-- V2025_09_09__update_anime_nullable_fields.sql
-- 애니메이션 테이블의 null 허용 필드들을 업데이트

-- total_episodes를 null 허용으로 변경
ALTER TABLE anime ALTER COLUMN total_episodes DROP NOT NULL;

-- release_date를 null 허용으로 변경
ALTER TABLE anime ALTER COLUMN release_date DROP NOT NULL;

-- rating을 null 허용으로 변경
ALTER TABLE anime ALTER COLUMN rating DROP NOT NULL;

-- rating_count를 null 허용으로 변경
ALTER TABLE anime ALTER COLUMN rating_count DROP NOT NULL;

-- duration을 null 허용으로 변경
ALTER TABLE anime ALTER COLUMN duration DROP NOT NULL;

-- source를 null 허용으로 변경
ALTER TABLE anime ALTER COLUMN source DROP NOT NULL;

-- synopsis를 null 허용으로 변경 (이미 nullable이지만 명시적으로 설정)
ALTER TABLE anime ALTER COLUMN synopsis DROP NOT NULL;

-- full_synopsis를 null 허용으로 변경 (이미 nullable이지만 명시적으로 설정)
ALTER TABLE anime ALTER COLUMN full_synopsis DROP NOT NULL;

-- broadcast_day를 null 허용으로 변경
ALTER TABLE anime ALTER COLUMN broadcast_day DROP NOT NULL;

-- broad_cast_time을 null 허용으로 변경
ALTER TABLE anime ALTER COLUMN broad_cast_time DROP NOT NULL;

-- season을 null 허용으로 변경
ALTER TABLE anime ALTER COLUMN season DROP NOT NULL;

-- year를 null 허용으로 변경
ALTER TABLE anime ALTER COLUMN year DROP NOT NULL;

-- type을 null 허용으로 변경
ALTER TABLE anime ALTER COLUMN type DROP NOT NULL;

-- country를 null 허용으로 변경
ALTER TABLE anime ALTER COLUMN country DROP NOT NULL;

-- language를 null 허용으로 변경
ALTER TABLE anime ALTER COLUMN language DROP NOT NULL;

-- poster_url을 null 허용으로 변경
ALTER TABLE anime ALTER COLUMN poster_url DROP NOT NULL;

-- 기존 데이터의 null 값들을 적절한 기본값으로 설정
UPDATE anime 
SET total_episodes = 1 
WHERE total_episodes IS NULL;

UPDATE anime 
SET rating = 0.0 
WHERE rating IS NULL;

UPDATE anime 
SET rating_count = 0 
WHERE rating_count IS NULL;

UPDATE anime 
SET duration = 24 
WHERE duration IS NULL;

UPDATE anime 
SET source = 'Unknown' 
WHERE source IS NULL;

-- release_date가 null인 경우 현재 날짜로 설정
UPDATE anime 
SET release_date = CURRENT_DATE 
WHERE release_date IS NULL;

-- synopsis가 null인 경우 빈 문자열로 설정
UPDATE anime 
SET synopsis = '' 
WHERE synopsis IS NULL;

-- full_synopsis가 null인 경우 빈 문자열로 설정
UPDATE anime 
SET full_synopsis = '' 
WHERE full_synopsis IS NULL;

-- country가 null인 경우 일본으로 설정
UPDATE anime 
SET country = '일본' 
WHERE country IS NULL;

-- language가 null인 경우 일본어로 설정
UPDATE anime 
SET language = '일본어' 
WHERE language IS NULL;
