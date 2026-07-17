# 보안 강화 런북 (2026-07-18) — Redis/RabbitMQ 인증 + Postgres 최소권한

이 파일의 파일 작업(compose 배선, SQL)은 완료돼 있음. 아래는 **사용자가 직접 실행**할
런타임 순서다. 배포는 반드시 `.\deploy.ps1` (3파일 스택). 맨손 `docker compose up` 금지.

시크릿 값은 이 문서에 박지 않는다 — 전부 `.env`에서 읽는다.

---

## 사전 준비 (.env)
이미 넣은 값: `REDIS_PASSWORD`, `RABBITMQ_PASSWORD`.
Postgres 최소권한까지 적용하려면 뒤 단계에서 `DB_APP_USERNAME`, `DB_APP_PASSWORD`도 추가한다(3단계).

---

## 1단계 — Redis + RabbitMQ 인증 적용

1. 시크릿 배포(재배포):
   .\deploy.ps1
   - deploy.ps1이 BLOCKED 2건(egress, frontend→postgres) 검증까지 통과해야 정상.
   - 이 시점에 Redis는 REDIS_PASSWORD로 인증 강제됨. 앱은 application-prod.yml의
     REDIS_PASSWORD로 접속하므로 자동 반영.

2. Redis 인증이 실제로 걸렸는지 확인(무인증 PING은 실패해야 정상):
   docker exec ott-redis redis-cli ping
   → NOAUTH Authentication required.  (이게 정상)
   docker exec ott-redis redis-cli -a "$((Get-Content .env | Select-String '^REDIS_PASSWORD=').ToString().Split('=',2)[1])" ping
   → PONG

3. RabbitMQ 기존 계정 비번 교체 — **중요**: RABBITMQ_DEFAULT_PASS는 볼륨 최초 초기화
   때만 먹으므로, 이미 존재하는 ott 계정은 아래로 직접 바꿔야 .env 값과 일치한다.
   $rpw = (Get-Content .env | Select-String '^RABBITMQ_PASSWORD=').ToString().Split('=',2)[1]
   docker exec ott-rabbitmq rabbitmqctl change_password ott $rpw
   - 앱은 1단계 재배포로 이미 새 RABBITMQ_PASSWORD를 갖고 있으므로, 교체 직후
     Spring AMQP가 자동 재연결한다(잠깐 인증 실패 로그는 정상).

4. 앱 상태 확인(로그에 redis/rabbit 인증 에러 없어야 함):
   docker logs --tail 80 ott-app

---

## 2단계 — Postgres 최소권한 계정 (분리형)

설계: flyway 마이그레이션(DDL)은 계속 root로, 앱 **런타임만** ott_app(DML 전용)으로.
prod.yml은 이미 배선됨 — DB_APP_* 미설정이면 root로 폴백(현재 동작 유지)이라,
아래 SQL로 계정을 먼저 만든 뒤 .env에 계정을 넣고 재배포하는 순서를 지킬 것.

1. 강한 비번 하나 정하고, SQL 파일의 자리표시자를 그 값으로 치환:
   security/postgres-least-privilege.sql 안의 `__SET_A_STRONG_PASSWORD__` 두 곳을 교체.
   (또는 psql 변수로 넘겨도 됨. 커밋 전 자리표시자로 되돌릴 것 — 비번을 git에 남기지 말 것.)

2. root로 접속해 계정+권한 생성:
   Get-Content security/postgres-least-privilege.sql -Raw | docker exec -i ott-postgres psql -U root -d ott_project_db

3. .env에 앱 계정 추가(비번은 1번에서 정한 값과 동일하게):
   DB_APP_USERNAME=ott_app
   DB_APP_PASSWORD=<1번에서 정한 값>

4. 재배포:
   .\deploy.ps1

5. 검증:
   - 앱이 ott_app으로 붙는지: docker logs --tail 80 ott-app  (연결 에러 없어야 함)
   - 앱 계정이 DDL 못 하는지(권한 분리 확인):
     docker exec ott-postgres psql -U ott_app -d ott_project_db -c "CREATE TABLE _perm_probe(x int);"
     → ERROR: permission denied for schema public  (이게 정상 = 최소권한 확인)
   - flyway는 root로 돌아 정상 마이그레이션(앱 기동 성공)이어야 함.

주의: SQL 파일 자리표시자를 실제 비번으로 바꿨다면 **커밋 전 되돌릴 것**.

---

## 3단계 — 시크릿 재암호화 (.env.enc 동기화)

.env.enc가 값의 단일 소스다. .env만 고치면 다음 CD가 옛 값을 풀어 드리프트가 난다.
반드시 재암호화 후 커밋한다(복호화의 역방향):

   sops --encrypt --input-type dotenv --output-type dotenv --output .env.enc .env

그다음:
   git add .env.enc docker-compose.prod.yml docker-compose.yml security/
   git commit -m "security: redis/rabbitmq auth + postgres least-privilege app account"

(.env.enc는 LF여야 하지만 .gitattributes가 커밋 시 LF로 정규화하므로 신경 안 써도 됨.)

---

## 롤백
- Redis: prod.yml redis command에서 --requirepass 제거 후 재배포. 또는 .env의 REDIS_PASSWORD를 비우면 무인증으로 복귀.
- Postgres: .env에서 DB_APP_USERNAME/DB_APP_PASSWORD 제거 후 재배포 → root로 즉시 폴백.

## 수용된 리스크
- Kafka: 단일 노드 + 루프백 전용 바인딩 + data망 격리로 접근을 통제한다. SASL 인증은
  비용 대비 효용상 이번 범위에서 미적용(수용된 리스크). 외부 노출/멀티노드로 갈 때 재검토.
