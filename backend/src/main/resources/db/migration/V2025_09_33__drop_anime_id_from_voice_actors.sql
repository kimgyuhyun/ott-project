-- V2025_09_33__drop_anime_id_from_voice_actors.sql
-- voice_actors 테이블에서 anime_id 컬럼 제거
-- 관계는 anime_voice_actors 같은 조인 테이블에서만 관리하도록 일원화

ALTER TABLE voice_actors
DROP COLUMN IF EXISTS anime_id;


