#!/bin/bash
# [2026-07-28] 최소권한 앱 계정(ott_app) 자동 생성 — security/postgres-least-privilege.sql 의 초기화 판본.
#
# 왜 .sh 인가: 계정명/비밀번호/DB명을 .env 에서 받아야 하는데 .sql 파일은
# 환경변수를 못 읽는다. 엔트리포인트는 initdb.d 안의 .sh 도 실행해준다.
#
# 왜 원본 .sql 과 내용이 다른가 (중요):
#   원본은 "이미 테이블이 다 있는 라이브 DB"에 손으로 거는 걸 전제한다.
#   이 스크립트는 반대로 flyway 가 돌기 전, 테이블이 하나도 없는 시점에 실행된다.
#   그래서 원본의 GRANT ... ON ALL TABLES/SEQUENCES 는 여기선 대상이 0건이라
#   의미가 없어 뺐다. 대신 ALTER DEFAULT PRIVILEGES 가 그 역할을 전부 한다 —
#   이후 flyway(root)가 만드는 모든 테이블/시퀀스에 DML 권한이 자동으로 붙는다.
#   같은 이유로 원본의 "이미 있으면 비번만 갱신" 분기도 뺐다. 볼륨 초기화 시
#   1회만 실행되므로 계정이 이미 있을 수 없다.
#
# 라이브 DB 에 소급 적용할 때는 이 스크립트가 아니라 원본 .sql 을 쓸 것.

set -e

: "${DB_APP_USERNAME:=}"
: "${DB_APP_PASSWORD:=}"

# 둘 중 하나라도 비면 계정을 만들지 않는다. 비밀번호 없는 로그인 계정이
# 생기는 것보다 낫다. 이 경우 앱은 prod.yml 의 폴백대로 root 로 붙는다(기존 동작).
if [ -z "$DB_APP_USERNAME" ] || [ -z "$DB_APP_PASSWORD" ]; then
    echo "[least-privilege] DB_APP_USERNAME/DB_APP_PASSWORD 미설정 — 앱 계정 생성을 건너뜁니다."
    exit 0
fi

echo "[least-privilege] 앱 런타임 계정 '$DB_APP_USERNAME' 생성 (소유자=$POSTGRES_USER)"

# :"var" = 식별자로 인용, :'var' = 문자열 리터럴로 인용. psql 이 알아서 이스케이프한다.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v role="$DB_APP_USERNAME" -v pw="$DB_APP_PASSWORD" \
     -v owner="$POSTGRES_USER" -v db="$POSTGRES_DB" <<-'EOSQL'
	-- 1) 런타임 계정: LOGIN 전용, 슈퍼유저 아님
	CREATE ROLE :"role" LOGIN PASSWORD :'pw';

	-- 2) 접속 + 스키마 사용. CREATE 는 주지 않는다 = 런타임 DDL 차단
	GRANT CONNECT ON DATABASE :"db" TO :"role";
	GRANT USAGE ON SCHEMA public TO :"role";

	-- 3) 앞으로 root(=flyway)가 만들 객체에 자동으로 DML 부여.
	--    신규 객체는 root 소유이므로 FOR ROLE root 로 걸어야 먹는다.
	ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA public
	    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"role";
	ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA public
	    GRANT USAGE, SELECT ON SEQUENCES TO :"role";
EOSQL

echo "[least-privilege] 완료"
