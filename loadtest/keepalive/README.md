# nginx→앱 연결 재사용(upstream keepalive) 전후 측정

> **적용 상태 (2026-10-09):** 이 측정의 결론이 둘 다 운영에 들어갔다.
> 롤링 교체 전 drain — #180, [ADR 0011](../../docs/adr/0011-drain-before-replace.md).
> upstream keepalive — #181. 운영 첫 배포 뒤 연결 유지(ESTABLISHED)와 nginx 가 30s 에 먼저 닫는 것을 확인했다.
> 아래 "질문"은 측정 당시의 운영 상태를 적은 것이다.

## 질문

측정 당시 운영 `nginx.prod.ha.conf` 는 upstream 에 `keepalive` 가 없고 백엔드 경로가 `Connection: close` 를
보낸다(`$connection_upgrade` map 이 Upgrade 없는 요청에 `close` 를 준다. 백엔드·프론트에 WebSocket·SSE 는 없다).
그래서 API 요청마다 nginx→앱 TCP 연결을 새로 맺는다. 재사용을 켜면

1. 무엇이 얼마나 줄어드는가 (새 연결 수, TIME_WAIT, 지연, CPU)
2. 롤링 교체(ADR 0002, 374요청 실패 0)가 여전히 무실패인가 — 그 실측은 연결 재사용이 없는 상태에서 나왔다

## 합격 기준 (측정 전에 적음, 2026-10-09)

| ID | 기준 | 성격 |
|---|---|---|
| C1 | 변경안에서 30 r/s 지속 부하 중 순차 교체 3회, 비200 응답 0건 | 필수. 실패하면 적용하지 않는다 |
| C2 | 30 r/s · 300 r/s 각각 측정 구간 p95 가 기준선 p95 의 +10% 이내, 오류율 0.1% 미만 | 필수(회귀 없음) |
| C3 | 300 r/s 정상상태에서 앱 쪽 TIME_WAIT 평균이 기준선의 10% 이하 | 효과 확인 |

판정: C1·C2 통과면 적용을 권한다. C1 이 깨지면 원인을 밝히고 보완책 없이는 적용하지 않는다.
지연 개선 폭 자체는 기준으로 삼지 않는다(같은 호스트 브리지라 1ms 미만으로 예상되고, 그 크기를 재는 것이 목적).

부하 수준 근거: 30 r/s 는 운영 `/api/` 의 `global_general` 리밋(30r/s, SNAT 때문에 서비스 전체 총량).
300 r/s 는 그 10배로 효과가 드러나는지 보기 위한 값이다. 용량(포화점) 측정이 아니므로 "충분하다"는 결론은 내지 않는다.

## 구성

| 항목 | 값 |
|---|---|
| 스택 | `docker-compose.e2e.yml` + 이 디렉터리의 오버레이, `-p ott-ka` (운영과 분리, default 망 internal) |
| 백엔드 | 2대(`ott-app`, `ott-app-2` 별칭), 운영 배포본과 같은 이미지 |
| nginx | 운영과 같은 이미지. `before.conf`(운영 HA 의 백엔드 경로 그대로) / `after.conf`(keepalive 16, keepalive_timeout 30s, `Connection ""`) |
| 운영과 다른 점 | TLS 없음, limit_req 없음, dev 프로파일, 데모 데이터(애니 1개) |
| 소켓 관측 | 앱 이미지는 distroless 라 앱 네트워크 네임스페이스에 붙인 프로브(`probe1/2`)로 `/proc/net/tcp` 를 읽는다 |
| 홉 지연 | nginx 접근 로그의 `$upstream_connect_time`, `$upstream_response_time` |

## 실행

```bash
export APP_IMAGE=ghcr.io/kimgyuhyun/ott-backend:<운영 배포 SHA>
export FRONT_IMAGE=ghcr.io/kimgyuhyun/ott-frontend:<같은 SHA>
F="-p ott-ka -f docker-compose.e2e.yml -f loadtest/keepalive/docker-compose.keepalive.yml"
KA_NGINX=before docker compose $F up -d
docker exec -i ott-e2e-postgres psql -U e2e -d ott_e2e -v pw="$LT_PASSWORD" -f - < loadtest/seed-users.sql

# 정상상태 측정 (RATE=30, 300)
k6 run -e LT_PASSWORD=$LT_PASSWORD -e RATE=300 loadtest/keepalive/keepalive.js
loadtest/keepalive/sample.sh <출력> <초>   # 같은 시간에 병행

# 변경안으로 전환 (nginx 만 재생성)
KA_NGINX=after docker compose $F up -d --force-recreate --no-deps nginx

# 정리
docker compose $F down -v
```

순차 교체는 `deploy-rolling.ps1` 과 같은 방식이다: `up -d --force-recreate --no-deps app` → nginx 안에서
`ott-app:8090/actuator/health` 가 UP 일 때까지 대기 → `app2` 같은 순서.

```bash
# 순차 교체 1회 (30 r/s 지속 부하 중). 결과는 results/<이름>/
loadtest/keepalive/rolling.sh <이름>
loadtest/keepalive/rolling-analyze.sh loadtest/keepalive/results/<이름>
```

| 환경변수 | 뜻 |
|---|---|
| `HEALTHY_GRACE=<초>` | health UP 후 다음 인스턴스로 넘어가기 전 대기(6초 대기 대안 시험용) |
| `DRAIN=1` | `deploy-drain.ps1` 의 `Set-UpstreamDrain` 을 그대로 불러 교체 전에 nginx 에서 뺀다. nginx 가 마운트한 `${KA_NGINX}.conf` 를 고쳐 쓰므로, 원본을 지키려면 사본으로 띄운다: `cp before.conf live.conf` 후 `KA_NGINX=live` (끝나면 `live.conf` 삭제) |

## 결과 — 2026-10-09

| 항목 | 값 |
|---|---|
| 백엔드 이미지 | `ghcr.io/kimgyuhyun/ott-backend:a448051413342464b34116291ad3979bca6431a1` (운영 배포본) |
| 저장소 HEAD | `02d673a` |
| 도구 | k6 (`results/*/k6-version`), 같은 호스트에서 실행. 운영 스택이 같은 호스트에서 돌고 있었다 |
| 데이터 | 데모 시드(애니 1개·무료 회차 3개) + 테스트 계정 500개 |
| 원시 자료 | `results/<회차>/` — k6 요약, nginx 접근 로그(`nginx-access.log.gz`, `analyze.sh`·`rolling-analyze.sh` 로 다시 볼 때는 `gunzip -k` 먼저), 소켓·CPU 표본, 교체 타임라인 |

### 정상상태

| 회차 | k6 p50 / p95 / p99 (ms) | 앱 쪽 TIME_WAIT 평균 | nginx 쪽 TIME_WAIT 평균 | 새 연결 없이 처리(connect_time 0) | 앱 CPU (2대 평균) |
|---|---|---|---|---|---|
| before-30 | 6.00 / 10.52 / 14.00 | 1,102 | 939 | 78% (해상도 1ms 라 "1ms 미만"의 뜻) | 12.2% |
| after-30 | 4.38 / 6.53 / 8.00 | 15 | 10 | 99.7% | 4.9% |
| before-30-r2 | 5.71 / 9.66 / 13.59 | 1,341 | 582 | 77% | 10.5% |
| after-30-r2 | 5.50 / 9.59 / 15.00 | 14 | 12 | 99.9% | 10.2% |
| before-300 | 4.50 / 10.01 / 23.50 | 12,805 | 3,183 | 80% | 50.4% |
| after-300 | 4.00 / 6.51 / 11.17 | 164 | 13 | 99.8% | 44.0% |

- 30 r/s 를 순서를 바꿔 다시 재니(r2) 지연·CPU 차이가 사라졌다. 첫 회차의 개선은 측정 순서와 호스트 부하 변동으로 본다. 300 r/s 는 재측정하지 않아 그 차이도 같은 이유일 수 있다 → **지연 개선은 입증하지 못했다.**
- 소켓 효과는 반복해도 같다. 변경안의 남은 앱 쪽 TIME_WAIT(300 r/s 에서 164)는 Tomcat `maxKeepAliveRequests` 기본 100 때문에 100요청마다 연결을 닫는 몫이다(300/100 × 60s = 180).
- nginx 로그로 본 연결 맺기 비용은 요청당 평균 약 0.2ms(1ms 해상도라 근사).

### 순차 교체

| 절차 | before | after |
|---|---|---|
| 현행(`Wait-Healthy` 직후 다음 인스턴스) | 51 / 0 / 0 | 27 / 0 / 51 |
| healthy 후 6초 대기 | 0 / 0 / 0 | 0 / 0 / 0 |

현행 절차의 실패는 두 설정 모두 `no live upstreams` 502 다. 스크립트는 직접 health 로 UP 을 보고 바로 다음
인스턴스를 내리는데, nginx 는 방금 올라온 인스턴스를 마지막 연결 실패 후 `fail_timeout`(5s) 동안 여전히
제외하고 있어 둘 다 후보에서 빠지는 창이 0~5초 생긴다(에러 로그 타이밍으로 3회 모두 겹침 유무와 결과가 일치).

### 판정

| ID | 결과 |
|---|---|
| C1 | 현행 절차로는 **불통과**(after 3회 중 2회 실패). 단 기준선도 같은 원인으로 실패하므로 keepalive 의 결함이 아니다. 대기 6초 절차에서는 3/3 통과 — 조건을 바꾼 재판정임을 명시한다 |
| C2 | 통과 (30 r/s r2: 9.59 vs 9.66ms, 300 r/s: 6.51 vs 10.01ms, 오류 0) |
| C3 | 통과 (앱 쪽 TIME_WAIT 12,805 → 164, 1.3%) |

## drain 방식 검증 — 2026-10-09

`deploy-rolling.ps1` 이 교체 직전에 해당 인스턴스를 nginx upstream 에서 `down` 으로 표시하고 reload 하도록
바꿨다(`deploy-drain.ps1` 의 `Set-UpstreamDrain`). 시험은 `rolling.sh` 에 `DRAIN=1` 을 주고, 같은 함수를
PowerShell 로 불러 nginx 가 마운트한 설정 파일(`live.conf`, `before.conf` 사본)에 적용했다. nginx 설정은
현행(keepalive 없음) 그대로다.

| 절차 | reload 횟수/배포 | 회차별 비200 (k6) | 다른 인스턴스로 재시도 (nginx) |
|---|---|---|---|
| 현행 | 0 | 51 / 0 / 0 | 19 / 16 / 17 |
| healthy 후 6초 대기 | 0 | 0 / 0 / 0 | 15 / 20 / 18 |
| drain 1차 (인스턴스마다 빼고 되돌림) | 4 | 0 / 0 / **1** | 0 / 0 / 0 |
| drain 2차 (다음 drain 이 앞 인스턴스 복구를 겸함) | 3 | 0 / 0 / 0 | 0 / 0 / 0 |

- drain 은 교체 중 요청이 내려가는 인스턴스로 아예 가지 않게 한다(재시도 0).
- drain 1차의 1건은 nginx 로그에 없는 요청이다. k6 쪽 `EOF`(POST)로, 시각이 첫 reload 와 일치한다.
  reload 때 이전 워커가 클라이언트 쪽 유휴 keep-alive 연결을 닫는데, 그 순간 그 연결로 요청을 보낸 경우다.
  reload 자체의 성질이라 현행 배포의 마지막 reload 에도 있는 노출이며, 2차에서 reload 를 3회로 줄였다.
  확률적이라 2차의 0/0/0 이 "다시 안 난다"는 증명은 아니다.
- 이 시험은 HTTP/1.1 이다. 운영의 클라이언트→nginx 는 HTTP/2 라 reload 때 GOAWAY 로 정리되므로 위 EOF 의
  양상이 다를 수 있다(검증 안 함).
- 시험이 실행한 것은 `Set-UpstreamDrain` 함수와 같은 순서의 절차다. `deploy-rolling.ps1` 의 루프와 실패 시
  복구(catch) 경로 자체는 운영 compose 조합에서만 돌아 여기서 실행하지 않았다.

## keepalive + drain — 2026-10-09

drain 이 들어간 절차(위 2차)에 `after.conf`(keepalive 16, keepalive_timeout 30s, `Connection ""`)를 얹어
같은 조건으로 순차 교체 3회를 돌렸다. 앱 이미지는 운영 배포본 그대로다(`server.tomcat.keep-alive-timeout: 60s`
명시는 이 이미지에 없지만, 미설정 기본값이 같은 60s 라 동작이 같다 — 운영에서 약 62s 에 닫힘을 실측).

| 절차 | 회차별 비200 (k6) | 다른 인스턴스로 재시도 (nginx) |
|---|---|---|
| keepalive, 현행 절차(drain 없음) | 27 / 0 / 51 | 18 / 16 / 14 |
| **keepalive + drain** | **0 / 0 / 0** | **0 / 0 / 0** |

합격 기준 C1(변경안에서 순차 교체 3회 비200 0건)을 처음 적은 조건 그대로 통과한다. 처음의 불통과는
keepalive 가 아니라 drain 이 없던 절차 탓이었다.
