# 부하 테스트

## 목표

| 항목 | 값 |
|---|---|
| 목표 부하 | 동시 시청자 500명 (진행률 저장 100 RPS) |
| 근거 | 가입자 1만 명 규모, 저녁 피크 동시 시청 5% 가정 |
| SLO | 목록/상세 p95 300ms, 스트림URL p95 500ms, 진행률 저장 p95 500ms, 에러율 0.1% 미만 |

동시 시청자 수는 진행률 저장 주기(5초, `frontend/src/app/player/page.tsx:158-166`)에서 역산한다.
VU 1명 = 0.2 RPS 이므로 500 VU = 100 RPS = 동시 시청자 500명.

## 시나리오 구성

VU 1명이 시청자 1명을 모사한다. 매 5초마다 진행률을 저장하고, 확률적으로 탐색과 재생 시작을 섞는다.

| 동작 | 비중 | 대상 |
|---|---|---|
| 진행률 저장 | ~72% | POST /api/episodes/{id}/progress |
| 목록/상세 조회 | ~20% | GET /api/anime, GET /api/anime/{id} |
| 스트림 URL 발급 | ~8% | GET /api/episodes/{id}/stream-url |
| 로그인 | VU당 1회 | POST /api/auth/login |

## 준비

### 1. 테스트 계정 심기

회원가입 API 는 이메일 인증 티켓을 요구해서(`EmailAuthService.java:57`) 500개를 API 로 만들 수 없다.
로그인은 `emailVerified` 를 보지 않고 `enabled` 만 보므로(`LocalUserDetailsService`) DB 에 직접 심는다.

```bash
docker exec -i ott-postgres psql -U root -d ott_project_db -v pw="$LT_PASSWORD" -f - < loadtest/seed-users.sql
```

계정 500개가 생성된다. 이메일은 `loadtest0001@loadtest.local` ~ `loadtest0500@loadtest.local`,
비밀번호는 전부 `$LT_PASSWORD` 로 넘긴 값이다. 이 값은 커밋하지 않는다 — k6 실행 시 같은 값을
`-e LT_PASSWORD=` 로 넘겨야 로그인이 된다.

**테스트가 끝나면 반드시 [정리](#정리)를 실행할 것.** 이 계정들은 `enabled=true` 라 실제로
로그인이 되고, 500개가 비밀번호를 공유한다. 운영 DB 에 방치하면 그대로 공격 표면이 된다.

### 2. k6 설치

```bash
winget install k6 --source winget
```

## 실행

### SLO 판정 (목표 500명에서 통과하는지)

```bash
k6 run -e LT_PASSWORD=... -e BACKENDS=http://127.0.0.1:8090,http://127.0.0.1:8093 loadtest/main.js
```

`BACKENDS` 로 백엔드를 직접 때리는 이유는 아래 [레이트 리밋](#레이트-리밋-단일-ip-로는-목표-부하가-안-만들어진다) 참고.
VU 가 인스턴스별로 나뉘어 로드밸런싱을 흉내낸다. 단일 인스턴스면 주소 하나만 준다.

### 포화점 탐색 (한계가 어디인지)

```bash
k6 run -e TEST=knee -e LT_PASSWORD=... -e BACKENDS=http://127.0.0.1:8090,http://127.0.0.1:8093 loadtest/main.js
```

100 → 250 → 500 → 1000 → 1500 VU 로 계단식으로 올린다.
**처리량(RPS)이 정체되는데 p95 만 급격히 올라가는 지점이 포화점**이다.

### nginx 를 포함한 전체 경로로 걸 때

```bash
k6 run -e LT_PASSWORD=... -e BASE=https://laputa.kozow.com loadtest/main.js
```

리밋 때문에 목표 부하가 안 나온다(아래 참고). 리밋 동작 자체를 확인할 때만 쓴다.
`ORIGIN` 은 `OriginValidationFilter` 허용 목록(`APP_CORS_ALLOWED_ORIGINS`)과 일치해야 한다.
안 맞으면 쓰기 요청이 전부 403 이라 진행률 저장이 하나도 안 들어간다.

## 결과 해석

k6 요약만으로는 "왜 느린지" 를 못 밝힌다. 부하 구간의 Grafana 를 같이 봐야 한다.

| 확인할 지표 | 꽉 찼다면 | 의심 지점 |
|---|---|---|
| DB 커넥션 풀 대기 | DB 병목 | 진행률 저장의 SELECT+UPDATE 2쿼리 |
| Postgres 느린 쿼리 | 특정 쿼리가 범인 | 목록 API 동적 필터 + offset 페이징 |
| 톰캣 스레드 사용률 | 스레드가 DB 대기로 묶임 | 위와 동일 |
| CPU | 계산 병목 | 로그인 BCrypt |
| Redis 지연 | 세션 조회 병목 | 인증 요청 전체 공통 경로 |
| GC 시간 | 메모리 압박 | 목록 API 큰 결과셋 |

CPU 도 남고 커넥션도 안 밀리는데 느리면 **락 경합**이다.

## 정리

```bash
docker exec -i ott-postgres psql -U root -d ott_project_db < loadtest/cleanup-users.sql
```

측정이 끝나면 미루지 말 것. 남겨두면 공용 비밀번호를 쓰는 로그인 가능 계정 500개가 운영에 방치된다.

계정과 함께 `episode_progress` 도 CASCADE 로 지워진다.

## 진단 스크립트

`diag-*.ps1` 은 부하 테스트가 아니라 세션 동작을 좁혀 보는 도구다. 비밀번호는 `LT_PASSWORD`
환경변수로 받는다(미지정 시 즉시 실패).

```bash
$env:LT_PASSWORD='...'; powershell -ExecutionPolicy Bypass -File .\loadtest\diag-rotation.ps1
```

| 스크립트 | 확인하는 것 |
|---|---|
| `diag-session.ps1` | 로그인 후 세션이 유지되는지 |
| `diag-rotation.ps1` | 로그인 쿠키가 첫 요청 뒤에도 살아있는지(세션 ID 이중 회전 재현용) |
| `diag-concurrent*.ps1` | 같은 계정의 두 세션이 공존하는지(동시 로그인 제한 재현용) |

## 레이트 리밋: 단일 IP 로는 목표 부하가 안 만들어진다

nginx 의 `limit_req` 는 `$binary_remote_addr` 기준이라 **출발지 IP 하나당** general 30 r/s(burst 50),
인증 경로는 strict 3 r/s(burst 5) 다. VU 500 명이 전부 같은 호스트에서 나오면 한 버킷을 공유하므로
목표 부하 115 RPS 중 30 r/s 만 통과하고 나머지는 차단된다.

실측(2026-07-27): nginx 경유로 SLO 를 돌렸더니 실패율 72.7%(38,058 건). 전부 리밋 차단이었고
`docker logs ott-nginx | grep "limiting requests"` 건수와 정확히 일치했다. 서버는 멀쩡했다.

다른 호스트(클라우드 VM 등)에서 걸어도 **똑같이 걸린다** — 여전히 IP 하나다. 해결책은 셋 중 하나:

| 방법 | 쓸 때 |
|---|---|
| `BACKENDS` 로 백엔드 직접 타격 | 앱·DB 용량을 잴 때. 설정 변경·배포 불필요. 대신 측정 경로에서 nginx 와 TLS 가 빠진다 |
| nginx 에 생성기 IP 예외 추가 | nginx 포함 전체 경로를 재야 할 때. 배포 필요하고 예외를 지우는 걸 잊으면 리밋이 뚫린 채 남는다 |
| 목표 부하를 리밋 안쪽으로 낮춤 | "동시 시청자 500명" 질문에 답을 못 하므로 권하지 않는다 |

용량 테스트와 리밋 테스트는 목적이 반대다. 섞지 말고 따로 돌린다.

## 세션 쿠키: 백엔드 직결이면 자가 쿠키를 버린다

세션 쿠키가 `Domain=laputa.kozow.com; Secure` 로 발급되기 때문에, `BACKENDS` 로 `127.0.0.1` 을
직접 때리면 k6 쿠키 자가 도메인 불일치로 쿠키를 버리고 **이후 인증 요청이 전부 401** 이 된다.
서버 결함이 아니라 nginx 를 건너뛴 대가다. `main.js` 는 이 경우 로그인 응답의 JSESSIONID 를
`BASE` 기준으로 자에 직접 심어서 해결한다.

## 주의사항

- **부하 생성기를 같은 호스트에서 돌리면 측정값이 오염된다.** 이 호스트에는 Postgres, Redis, Kafka,
  RabbitMQ, Prometheus, Grafana, Loki 가 같이 돌고 있고 k6 까지 CPU 를 뺏어간다. 가능하면 다른 머신에서
  걸고, 어쩔 수 없이 같은 호스트에서 돌린다면 k6 프로세스 CPU 사용률을 같이 기록해서 "생성기 병목이
  아님" 을 확인해야 한다.
- 여기서 나온 **절대 RPS 는 이 호스트 사양에서만 유효하다.** 쓸 수 있는 건 개선 전후 비율과 병목 원인
  분석이다.
- 로그인 실패가 누적되면 `LoginAttemptService` 가 계정을 잠그고(429) 이후 Turnstile 토큰을 요구한다.
  `login_failed` 임계값이 깨지면 즉시 중단하고 계정 시딩부터 확인할 것.
- 진행률 저장은 실제 DB 에 쓴다. 운영 데이터와 섞이지만 `loadtest%@loadtest.local` 계정 것만 생기므로
  cleanup 으로 전부 회수된다.
