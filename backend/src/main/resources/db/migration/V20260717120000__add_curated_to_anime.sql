-- 운영자 큐레이션 여부 플래그
--
-- 배경: TMDB 자동 보강(AnimeEnhancementService)이 운영자가 손본 제목/포스터를 되돌리는 문제가 있다.
-- 이 플래그가 켜진 작품은 보강 대상에서 제외되어, 외부 API 가 운영자 판단을 덮어쓰지 못한다.
--
-- 기존 행은 전부 자동 수집분이므로 DEFAULT FALSE 로 백필된다.
-- ddl-auto: validate 이므로 이 컬럼이 있어야 Anime.curated 매핑 검증이 통과한다.
ALTER TABLE anime ADD COLUMN curated BOOLEAN NOT NULL DEFAULT FALSE;
