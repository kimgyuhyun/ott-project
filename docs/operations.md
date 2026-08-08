# 운영 가이드

## 보안 설정

설계 의도와 전체 목록은 `docs/security.md` 에 있다. 여기에는 운영 중 확인할 항목만 둔다.

### 네트워크 보안
- **포트 포워딩**: 운영은 80/443만 포워딩
- **SSH 접근**: 22번 포트는 닫고 GitHub Actions self-hosted runner로만 배포
- **내부 서비스**: 백엔드(8090), 프론트(3000), DB(5432), Redis(6379), Kafka, RabbitMQ는 컨테이너 내부 통신만 사용

### 개발 환경 보안
- **DB/Redis 외부 노출 방지**: `docker-compose.yml`에서 `127.0.0.1:5432:5432`, `127.0.0.1:6379:6379`로 루프백 바인딩
- **모바일 테스트**: LAN에서만 필요 시 `3000/8090` 접근 허용 (방화벽 프라이빗만)
- **DB/Redis 보안**: 반드시 루프백 유지

### 보안 헤더
- **적용 파일**: `nginx/nginx.prod.conf` — 헤더 목록과 의도는 `docs/security.md` 참고

## 환경 변수 관리

### 설정 파일
- **예시 파일**: `env.example`
- **주요 항목**:
  - DB (PostgreSQL)
  - Redis
  - OAuth2 (구글/카카오/네이버)
  - TMDB
  - BASE_URL/COOKIE_DOMAIN
  - Iamport

### 비밀키 관리
- **방식**: SOPS+age로 암호화한 `.env.enc`를 git에 커밋(값의 단일 소스). CD가 GitHub Secret `AGE_KEY`(age 개인키)로 복호화해 배포 시 `.env` 생성
- **보안**: 진짜 값은 `.env.enc` 한 곳에만 존재하고 GitHub엔 개인키만 보관 → 평문 원본이 어긋날 여지 없음

## 모니터링

`docker-compose.monitoring.yml` 로 Prometheus + Grafana + Loki 를 함께 띄운다.
배포 시 이 파일을 빼면 `--remove-orphans` 가 스택을 걷어가므로 `deploy.ps1` 이 항상 함께 묶는다.

- **지표(Prometheus)**: 백엔드 actuator 메트릭 수집. 응답 시간·에러율·JVM·커넥션 풀
- **로그(Loki)**: 애플리케이션이 push 방식으로 전송. 컨테이너 로그를 긁는 방식이 아니다
- **대시보드(Grafana)**: `127.0.0.1:3001` 루프백 바인딩. 외부 노출 없음(접근은 호스트에서만)
- **볼륨**: `prometheus_data`, `grafana_data`, `loki_data` 로 재시작 후에도 유지

### 경보

- **규칙 파일**: `monitoring/rules/*.yml` → 컨테이너의 `/etc/prometheus/rules` 에 디렉터리로 마운트, `monitoring/prometheus.yml` 의 `rule_files` 가 읽는다
- **반영**: 배포 스크립트가 `promtool check config` 후 `docker kill -s HUP ott-prometheus`(정지 아님, 재읽기 신호). 규칙 파일은 bind mount 라 내용만 바뀌면 컨테이너가 재생성되지 않는다 — 리로드가 없으면 배포는 성공했는데 옛 규칙이 계속 돈다
- **적재 확인**: `curl -s http://127.0.0.1:9090/api/v1/rules` — `promtool` 통과는 규칙이 실제로 로드됐다는 뜻이 아니다(빈 디렉터리도 유효한 설정이다)
- **화면**: Prometheus `127.0.0.1:9090/alerts`, Grafana 의 Alerting > Alert rules(데이터소스 관리 규칙)

**지금 거는 것 — 기준선이 필요 없는 절대값만**

| 경보 | 조건 | 근거 |
|---|---|---|
| `KafkaDlqInflow` | DLT 토픽 발행 24시간 합 > 0 | 정상값이 정의상 0. DLT 에는 소비자가 없어 자동 복구되지 않는다 |
| `DiskSpaceLow` / `DiskSpaceCritical` | 사용률 85%(15분) / 95%(5분) | 기준이 트래픽이 아니라 100% 라는 물리적 상한이다 |
| `BillingBatchStalled` | 마지막 완주로부터 8시간 경과 | 정상 주기가 관측이 아니라 cron(6h)으로 정의돼 있다. 한 번 거른 것은 봐주고 두 번째부터 잡는 값 |

정기결제 배치는 사람이 트리거하지 않는 유일한 매출 경로라, 조용히 멈춰도 화면에 아무 변화가 없다(청구가 안 되면 오히려 조용하다). 그래서 지표 `billing_batch_last_success_timestamp_seconds` 를 두고 완주했을 때만 갱신한다 — 예외로 죽든 스케줄러가 발화하지 않든 값이 늙는 것은 같으므로 한 신호로 잡힌다. ShedLock 때문에 매 주기 두 인스턴스 중 하나만 실제로 돌므로 경보 식은 인스턴스별로 보지 않고 `max` 로 접는다.

DLQ 는 "크기"가 아니라 "유입"을 본다. 브로커측 큐 깊이를 주는 익스포터가 스택에 없어서, 백엔드 프로듀서 지표(`kafka_producer_topic_record_send_total`)의 topic 라벨로 DLT 발행 건수를 센다. 배포로 컨테이너가 교체되면 시계열이 끊겨 경보도 사라진다(적재는 DLT 에 그대로 남는다).

**미룬 것과 거는 시점**

| 미룬 경보 | 왜 | 언제 |
|---|---|---|
| 오류율, 응답시간 p95 | 임계값이 정상 구간 기준선 위에서 정해져야 한다. 임의의 숫자는 오탐을 만들고, 반복되는 오탐은 경보를 무시하게 만든다 | 정상 운영 2주 관측 후(2026-08-06 기준 → 2026-08-20 이후). 지표는 이미 수집 중(`http_server_requests_seconds_*`) |
| 인증 실패 급증 | "급증"의 기준선이 없다. 추가로 인증 실패는 전용 카운터가 없어 `http_server_requests_seconds_count` 의 상태코드로 유도해야 한다 | 위와 같은 시점에, 로그인 경로 실패율 기준선과 함께 |
| 배포 실패 | Prometheus 지표가 아니다. CD(GitHub Actions) 실패는 워크플로 알림 경로에서 잡아야 한다 | 알림 채널 결정과 함께(아래) |

**알려진 한계(후속 과제)**

- **발송 경로 없음**: Alertmanager 가 없어 firing 되어도 화면에만 뜬다. 호스트에는 이미 동작하는 채널이 있다 — `security/ott-watchdog.ps1` 의 디스코드 웹훅. Alertmanager 를 붙이거나 웹훅으로 보내는 결정이 필요하다
- **백엔드가 전부 내려가면 `BillingBatchStalled` 는 울리지 않는다**: 시계열 자체가 사라져 식이 아무것도 반환하지 않는다. 배치가 "죽은 것"은 잡지만 앱이 "없는 것"은 못 잡는다. `up{job="ott-backend"}` 기반 경보가 따로 필요하다(위 발송 경로 결정과 함께)
- **호스트 디스크는 감시되지 않는다**: 위 디스크 경보는 백엔드 컨테이너가 보는 파일시스템 기준이다. Docker Desktop(WSL2)에서 컨테이너의 `df` 는 ext4 vhdx 의 최대 크기를 총량으로 보고한다. 2026-08-06 실측 — 컨테이너 기준 1007GiB 중 16% 사용, 같은 시각 호스트 C: 는 930GB 중 80% 사용(여유 185GB). 호스트가 먼저 차면 이 경보는 울리지 않는다. 호스트측 감시(워치독에 임계 검사 추가 또는 windows_exporter)가 따로 필요하다

### 가용성 감시
- `security/ott-watchdog.ps1`: 서비스 상태 감시, 이상 시 알림
- `security/ott-uptime.ps1`: 가동률 기록

## 백업과 복구

### DB 백업
- **스크립트**: `security/db-backup.ps1`
- **방식**: `pg_dump` → age 로 암호화 → Cloudflare R2 업로드
- **주기/보존**: 매일 새벽, 30일 보존 (호스트 예약 작업)
- **복구**: R2에서 받아 age 개인키로 복호화 후 restore. 개인키는 repo·서버가 아닌 별도 비밀번호 관리자에 보관한다

### 로그 백업
- **스크립트**: `security/log-backup.ps1` — 로그를 호스트 밖으로 내보내 침해 시 삭제·조작에 대비
