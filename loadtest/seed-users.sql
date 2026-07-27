-- 부하 테스트용 계정 500개 생성
-- 로그인은 emailVerified 를 검사하지 않고 enabled 만 보므로(LocalUserDetailsService),
-- 이메일 인증 흐름 없이 DB 에 직접 심어도 로그인이 된다.
-- BCrypt 해시는 1회만 계산해서 재사용한다(500번 계산하면 시딩만 30초 넘게 걸림).
--
-- 비밀번호는 실행할 때 넘긴다(소스에 두지 않는다):
--   psql -v pw=원하는비밀번호 -f seed-users.sql
-- k6 실행 시 같은 값을 -e LT_PASSWORD=... 로 넘겨야 한다.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- psql 변수는 달러 인용($$) 블록 안에서 치환되지 않는다. 세션 설정으로 넘겨서 꺼내 쓴다.
SELECT set_config('loadtest.pw', :'pw', false);

DO $$
DECLARE
    hashed text;
BEGIN
    hashed := crypt(current_setting('loadtest.pw'), gen_salt('bf', 10)); -- $2a$10$... (Spring BCryptPasswordEncoder 호환)

    INSERT INTO users (email, password, name, role, auth_provider, email_verified, enabled)
    SELECT
        format('loadtest%s@loadtest.local', lpad(i::text, 4, '0')),
        hashed,
        format('부하테스트%s', i),
        'USER',
        'LOCAL',
        true,
        true
    FROM generate_series(1, 500) AS i
    ON CONFLICT (email) DO NOTHING;
END $$;

SELECT count(*) AS loadtest_users FROM users WHERE email LIKE 'loadtest%@loadtest.local';
