-- [2026-07-28] 쿼리 관측용 확장.
--
-- /docker-entrypoint-initdb.d 에 마운트되어 있어서, postgres 데이터 볼륨을
-- 새로 만들 때 딱 한 번 자동 실행된다(기존 볼륨에는 실행되지 않는다).
-- 실행 주체는 POSTGRES_USER(root, 슈퍼유저)이고 대상 DB는 POSTGRES_DB 이므로
-- 접속 정보를 따로 적을 필요가 없다.
--
-- 라이브러리 로드(shared_preload_libraries)는 docker-compose.yml 의 postgres
-- command 에 있다. 확장 생성만으로는 동작하지 않으니 둘을 같이 봐야 한다.

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
