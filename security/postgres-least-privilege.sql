-- [SECURITY 2026-07-18] Postgres 최소권한 앱 계정 (분리형)
--
-- 목적: 앱 런타임 커넥션을 슈퍼유저(root)에서 DML 전용 계정(ott_app)으로 낮춘다.
--       flyway 마이그레이션은 계속 root(권한 계정)로 돌므로 DDL/ALTER 권한은
--       ott_app 에 주지 않는다. 즉 뚫린 앱이 런타임에 스키마를 바꾸거나
--       테이블을 드롭할 수 없다. 기존 테이블 소유권은 root 에 그대로 둔다.
--
-- 실행 위치: prod DB(ott_project_db)에 root(슈퍼유저)로 접속해서 실행.
--   docker exec -i ott-postgres psql -U root -d ott_project_db < security/postgres-least-privilege.sql
--
-- 주의: 아래 '__SET_A_STRONG_PASSWORD__' 를 반드시 강한 값으로 바꾼 뒤 실행할 것.
--       그리고 그 값을 .env 의 DB_APP_PASSWORD 에 동일하게 넣어야 앱이 접속된다.

-- 1) 앱 런타임 계정 생성 (LOGIN 전용, 슈퍼유저 아님)
--    이미 있으면 비번만 갱신하도록 분기.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ott_app') THEN
        CREATE ROLE ott_app LOGIN PASSWORD '__SET_A_STRONG_PASSWORD__';
    ELSE
        ALTER ROLE ott_app LOGIN PASSWORD '__SET_A_STRONG_PASSWORD__';
    END IF;
END
$$;

-- 2) DB 접속 + 스키마 사용 권한 (CREATE 는 주지 않는다 = 런타임 DDL 차단)
GRANT CONNECT ON DATABASE ott_project_db TO ott_app;
GRANT USAGE ON SCHEMA public TO ott_app;

-- 3) 기존 테이블/시퀀스에 DML 권한 (SELECT/INSERT/UPDATE/DELETE, 시퀀스는 채번용)
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ott_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ott_app;

-- 4) 앞으로 root(=flyway)가 만들 테이블/시퀀스에도 자동으로 DML 부여
--    (분리형의 핵심: 신규 객체는 root 소유이므로 FOR ROLE root 로 걸어야 먹는다)
ALTER DEFAULT PRIVILEGES FOR ROLE root IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ott_app;
ALTER DEFAULT PRIVILEGES FOR ROLE root IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO ott_app;

-- 확인용(선택): 부여된 권한 조회
-- \dp public.*
