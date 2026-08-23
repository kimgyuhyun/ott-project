-- E2E 테스트 계정 1개 생성
--
-- 로그인은 emailVerified 를 보지 않고 enabled 만 보므로(LocalUserDetailsService),
-- 이메일 인증 흐름 없이 DB 에 직접 심어도 로그인이 된다. 회원가입 API 로는 만들 수 없다 -
-- 인증 티켓을 요구한다(EmailAuthService).
--
-- 비밀번호는 실행할 때 넘긴다(소스에 두지 않는다):
--   docker exec -i ott-postgres psql -U root -d ott_project_db \
--     -v pw="$E2E_PASSWORD" -f - < frontend/e2e/seed-e2e-user.sql
-- Playwright 실행 시 같은 값을 E2E_PASSWORD 로 넘겨야 한다.
--
-- ⚠ 운영 DB 에 심지 않는다. 이 계정은 실제로 로그인이 되므로 그대로 공격 표면이 된다.
--    E2E 는 개발 스택에 쏜다(frontend/e2e/README.md 참고).
--    배포 후 프로덕션 확인은 deploy-rolling.ps1 의 읽기 전용 스모크가 담당한다.
--
-- 부하 테스트 쪽(loadtest/seed-users.sql)과 계정을 나눠 둔 이유: 저쪽은 500개를 만들고
-- 진행률 행을 대량으로 남긴다. 섞으면 E2E 가 남의 시청 이력을 물려받아 "이어보기" 분기를 타서
-- 재생 버튼 문구가 달라진다.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- psql 변수는 달러 인용($$) 블록 안에서 치환되지 않는다. 세션 설정으로 넘겨서 꺼내 쓴다.
SELECT set_config('e2e.pw', :'pw', false);

DO $$
DECLARE
    hashed text;
BEGIN
    hashed := crypt(current_setting('e2e.pw'), gen_salt('bf', 10)); -- $2a$10$... (Spring BCryptPasswordEncoder 호환)

    INSERT INTO users (email, password, name, role, auth_provider, email_verified, enabled)
    VALUES ('e2e@e2e.local', hashed, 'E2E 테스트', 'USER', 'LOCAL', true, true)
    ON CONFLICT (email) DO UPDATE SET password = EXCLUDED.password, enabled = true;
END $$;

SELECT email, enabled FROM users WHERE email = 'e2e@e2e.local';
