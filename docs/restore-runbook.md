# 복원 런북

DB 백업으로 실제로 되살리는 절차. 평상시 점검(`복원 훈련`)과 진짜 장애 복구(`실제 복구`)를 함께 다룬다.

백업을 뜨는 쪽은 `security/db-backup.ps1`, 개요는 `operations.md` 의 "백업과 복구" 절에 있다.
이 문서는 그 백업을 **되돌리는** 쪽만 다룬다.

## 왜 이 문서가 있나

2026-08-30 에 복원 훈련을 처음 만들어 돌렸더니, 그때까지의 백업이 **복원되지 않는 상태**였다.

```
psql:<stdin>:189145: ERROR:  role "ott_app" does not exist
```

`pg_dump` 는 데이터베이스 하나만 뜬다. 롤은 클러스터 단위라 거기 포함되지 않는데, 덤프 안에는
최소권한 런타임 계정 `ott_app` 을 참조하는 `GRANT` 가 87 줄 있었다. 새 서버에 복원하면 거기서 멈춘다.

그 몇 달 동안 백업은 매일 "backup OK" 를 찍고 있었다. 유일한 검증이 "파일이 0바이트가 아니다"
였기 때문이다. **백업이 있다는 것과 복원이 된다는 것은 다른 얘기다** — 그 차이를 정기적으로
확인하려고 이 절차를 둔다.

## 백업의 구성

한 번의 백업이 R2(`r2:ott-db-backups`)에 **파일 두 개**를 올린다. 둘 다 age 로 암호화돼 있다.

| 파일 | 내용 | 없으면 |
|---|---|---|
| `db_<시각>.sql.age` | 데이터베이스 `ott_project_db` 전체 | 데이터가 없다 |
| `globals_<시각>.sql.age` | 클러스터 롤(`root`, `ott_app`)과 비밀번호 해시 | 복원이 GRANT 에서 멈춘다 |

**globals 는 항상 데이터베이스 덤프보다 먼저 적용한다.** 롤이 있어야 `GRANT` 와 `OWNER TO` 가 통과한다.

> **2026-08-30 이전 백업에는 `globals_*` 가 없다.** 그 백업들은 이 절차로 복원할 수 없다.
> 필요하면 롤을 손으로 만들어야 하는데, 비밀번호 해시가 없으므로 앱 계정 비밀번호를 새로
> 정하고 `.env` 의 `DB_APP_PASSWORD` 도 함께 바꿔야 한다.

## 준비물

- **age 개인키** — 이 호스트에도, 저장소에도 없다. 비밀번호 관리자에서 꺼내 임시 파일로 만들어
  쓰고, 끝나면 지운다. 백업을 푸는 열쇠를 백업 옆에 두지 않는 것이 의도된 설계다.
- `rclone` (R2 접근), `age` (복호화), Docker. 셋 다 이 호스트에 설치돼 있다.

---

## 1. 매일 점검 (개인키 불필요)

```powershell
.\security\restore-drill.ps1 -Mode Check
```

R2 의 최신 백업이 **존재하는가 / 26시간 안쪽인가 / age 파일 형식이 맞는가 / 직전보다 10% 넘게
줄지 않았는가**를 본다. 복호화를 못 하므로 "복원이 된다"는 말은 할 수 없고, **"백업이 계속 오고
있다"**만 보증한다. `db-backup.ps1` 뒤에 이어서 돌도록 호스트 예약 작업에 넣는다.

실패하면:

| 메시지 | 뜻 | 할 일 |
|---|---|---|
| `no backups found` | R2 에 덤프가 없다 | rclone 설정과 백업 작업 등록 상태 확인 |
| `latest backup is N hours old` | 백업이 멈췄다 | 예약 작업 로그, Discord 알림 이력 확인 |
| `does not look like an age file` | 엉뚱한 것이 올라갔다 | `db-backup.ps1` 의 age 단계 확인 |
| `more than 10% smaller` | 덤프가 잘렸다 | 즉시 `-Mode Full` 로 실제 복원 확인 |

## 2. 분기 훈련 (개인키 필요)

```powershell
.\security\restore-drill.ps1 -Mode Full -KeyFile C:\temp\age-key.txt
```

R2 최신 백업을 받아 복호화하고, **격리된 빈 postgres**(`-p ott-restore-test`, 호스트 포트 없음,
internal 망)에 globals → 데이터베이스 순으로 복원한 뒤 검증하고 철거한다. 실서비스는 건드리지
않는다. 끝나면 키 파일을 지운다.

검증 항목은 아래와 같다. 하나라도 어긋나면 0 이 아닌 코드로 끝난다.

- `public` 스키마 테이블 수가 살아있는 DB 와 같은가
- Flyway 최신 버전이 같은가 / 실패한 마이그레이션이 0 인가
- `anime` · `episodes` · `plans` · `users` 에 행이 있는가 (스키마만 오고 `COPY` 가 빠진 경우를 잡는다)
- `ott_app` 롤이 있는가
- `ott_app` 이 `public.anime` 을 `SELECT` 할 수 있는가 (롤만 있고 GRANT 가 안 붙은 경우를 잡는다)

행 수는 살아있는 DB 와 비교하지 않는다. 덤프는 새벽 4시 것이고 그 뒤로 계속 늘어나므로 다른 것이
정상이다. 스키마와 마이그레이션 상태는 그 사이에 바뀌지 않으므로 정확히 같아야 한다.

훈련 스크립트 자체를 고친 뒤에는 키 없이 절차만 시험할 수 있다:

```powershell
.\security\restore-drill.ps1 -Mode Full -DumpFile .\dump.sql -GlobalsFile .\globals.sql
```

---

## 3. 실제 복구

데이터가 실제로 날아갔을 때. **훈련과 다른 점은 대상이 실서비스라는 것뿐이고, 순서는 같다.**

### 3-1. 서비스를 멈춘다

```powershell
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.netlock.yml -f docker-compose.ha.yml -f docker-compose.monitoring.yml stop app app2
```

백엔드만 멈춘다. 복원 중에 앱이 쓰기를 시도하면 복원 결과가 오염된다.

### 3-2. 백업을 받아 복호화한다

```powershell
rclone lsf r2:ott-db-backups --include "db_*.sql.age" | Sort-Object   # 최신 이름 확인
rclone copy r2:ott-db-backups/db_<시각>.sql.age      C:\temp\
rclone copy r2:ott-db-backups/globals_<시각>.sql.age C:\temp\
age -d -i C:\temp\age-key.txt -o C:\temp\db.sql      C:\temp\db_<시각>.sql.age
age -d -i C:\temp\age-key.txt -o C:\temp\globals.sql C:\temp\globals_<시각>.sql.age
```

`db_` 와 `globals_` 의 **시각이 같아야 한다.** 짝이 안 맞으면 롤과 GRANT 가 어긋날 수 있다.

### 3-3. 빈 DB 를 만든다

기존 볼륨을 지우고 새로 뜨게 한다. **되돌릴 수 없으니 지우기 전에 현재 볼륨을 먼저 백업한다.**

```powershell
docker compose ... stop postgres
docker volume rm ott-project_postgres_data     # 되돌릴 수 없음
docker compose ... up -d --no-deps postgres
```

### 3-4. globals 를 먼저 적용한다

```powershell
cmd /c "docker exec -i ott-postgres psql -U root -d ott_project_db -f - < C:\temp\globals.sql"
```

`ON_ERROR_STOP` 을 켜지 않는다. 이 경로에서는 **`role "root" already exists` 오류가 한 번 나는 것이
정상이다** — 컨테이너 entrypoint 가 `POSTGRES_USER=root` 로 이미 만들어 뒀기 때문이다. 그
한 줄 말고 다른 오류가 나면 멈추고 원인을 본다.

적용됐는지 확인한다:

```powershell
docker exec ott-postgres psql -U root -d ott_project_db -tAc "select rolname from pg_roles where rolname='ott_app';"
```

`ott_app` 이 나와야 다음으로 간다.

### 3-5. 데이터베이스를 복원한다

```powershell
cmd /c "docker exec -i ott-postgres psql -v ON_ERROR_STOP=1 -U root -d ott_project_db -f - < C:\temp\db.sql > C:\temp\restore.log 2>&1"
```

**여기서는 `ON_ERROR_STOP=1` 을 반드시 켠다.** 이게 없으면 psql 은 오류를 흘려보내고 종료 코드 0 으로
끝나서, 절반만 복원된 DB 를 "성공"으로 읽게 된다. 실패하면 `restore.log` 의 마지막 줄을 본다.

### 3-6. 확인하고 서비스를 올린다

```powershell
docker exec ott-postgres psql -U root -d ott_project_db -tAc "select count(*) from information_schema.tables where table_schema='public';"   # 49
docker exec ott-postgres psql -U root -d ott_project_db -tAc "select max(version) from flyway_schema_history;"
docker exec ott-postgres psql -U root -d ott_project_db -tAc "select has_table_privilege('ott_app','public.anime','SELECT');"                # t
.\deploy-rolling.ps1
```

마지막 스크립트가 백엔드를 다시 띄우고 보안 불변식과 공개 진입점까지 확인한다.

### 3-7. 뒤처리

- `C:\temp` 의 평문 덤프와 globals, **age 키 파일을 지운다.** globals 에는 롤 비밀번호 해시가 들어 있다.
- 복구 시점과 유실 구간(마지막 백업 이후)을 `incident-*.md` 로 남긴다.

---

## 함정

이 절차를 만들면서 실제로 밟은 것들이다.

- **`docker cp` 는 tmpfs 마운트에서 파일을 읽지 못한다** (이 호스트 기준. 같은 파일을 볼륨에 두면
  읽힌다). postgres 가 `read_only` 가 되면서 `pg_dump -f /tmp/...` + `docker cp` 방식이 깨졌고,
  `/tmp` tmpfs 를 추가해도 고쳐지지 않았다. 그래서 백업이 표준출력 스트리밍으로 바뀌었다.
  컨테이너 안에 쓰기 경로가 아예 필요 없는 쪽이 옳은 수정이다.
- **리다이렉트는 `cmd /c` 에 맡긴다.** PowerShell 로 바로 받으면 출력 인코딩을 타서 덤프가 손상될 수
  있다. `log-backup.ps1` 이 `docker logs` 에 쓰는 것과 같은 방식이다.
- **`ON_ERROR_STOP` 없이 복원하면 실패가 성공으로 보인다.** 3-5 에서 반드시 켠다.
- **globals 의 `CREATE ROLE root` 는 대상 클러스터의 부트스트랩 계정과 충돌한다.** 훈련 스택은
  부트스트랩을 `drill_admin` 으로 둬서 이걸 피하고 `ON_ERROR_STOP=1` 을 끝까지 유지한다.
  실제 복구(3-4)에서는 오류 한 줄을 예상하고 넘긴다.
- **`pg_dumpall --globals-only` 는 비밀번호 해시를 담는다.** 평문으로 남기지 않는다.

## 관련 문서

- `operations.md` — 백업 주기·보존, 감시와 경보
- `security.md` — 시크릿 관리와 최소권한 DB 계정
- `deployment.md` — 배포 절차와 롤링 교체
