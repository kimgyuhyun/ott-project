# 배포 가이드

## Docker 배포

### 컨테이너 구성
- **Nginx**: 리버스 프록시, SSL 터미네이션
- **Backend**: Spring Boot 애플리케이션 (포트 8090)
- **Frontend**: Next.js 애플리케이션 (포트 3000)
- **PostgreSQL**: 데이터베이스 (포트 5432)
- **Redis**: 캐시 서버 (포트 6379)
- **Kafka**: 결제 부수효과 이벤트 스트림 (KRaft 단일 노드). `docs/messaging.md` 참고
- **RabbitMQ**: 정기결제 실패 재시도 지연 큐 (TTL+DLX). `docs/messaging.md` 참고

### 포트 설정
- **공개 포트**: 80 (HTTP), 443 (HTTPS)만 외부 노출
- **내부 통신**: 백엔드(8090), 프론트(3000), DB(5432), Redis(6379), Kafka, RabbitMQ는 컨테이너 내부 통신만 사용

### 실행 명령 (중요)
- **운영 배포(권장)**: `.\deploy.ps1` — 아래 4개 파일을 고정으로 묶어 egress 차단과 모니터링까지 적용
  - `docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.netlock.yml -f docker-compose.monitoring.yml up -d`
  - monitoring 파일을 빼고 올리면 `--remove-orphans` 가 모니터링 스택을 걷어낸다. 반드시 함께 묶을 것.
- **개발**: `docker compose -f docker-compose.yml -f docker-compose.dev.yml up`
- **주의**: 플래그 없는 `docker compose up` 은 배포 명령이 아니다.
  netlock(egress 차단)이 빠져 프론트 인터넷 접근이 다시 열리므로 사용하지 말 것.
- **환경 변수**: `env.example` 참고. 실제 값은 `.env.enc`(SOPS+age)에서 복호화해 `.env` 로 주입.

### 무중단 배포
- **스크립트**: `.\deploy-rolling.ps1` — 백엔드 2인스턴스(`docker-compose.ha.yml`)를 **순차 교체**한다
- **인스턴스를 2개로 늘리는 것만으로는 무중단이 되지 않는다.** 두 인스턴스를 동시에 재생성하면
  교체 구간이 그대로 노출된다 (실측: 동시 재기동 시 197요청 중 164건 502)
- 순차 교체 + nginx `proxy_next_upstream` 을 함께 적용해야 성립한다 (실측: 374요청 0건 실패)
- ha 오버레이를 맨손 `up` 으로 올리면 동시 재생성이 되므로 반드시 스크립트를 쓸 것

### compose 파일 구성
| 파일 | 용도 | 배포 시 사용 |
|---|---|---|
| `docker-compose.yml` | 기본 서비스 정의 | O |
| `docker-compose.prod.yml` | 운영 오버레이 | O |
| `docker-compose.netlock.yml` | egress 차단·망분리 | O |
| `docker-compose.monitoring.yml` | Prometheus/Grafana/Loki | O |
| `docker-compose.dev.yml` | 개발용 오버레이 | X |
| `docker-compose.ha.yml` | 백엔드 2인스턴스 오버레이 (무중단 배포용) | 필요 시 |
| `docker-compose.multi.yml` | **실험 전용** 독립 스택. 다중 인스턴스에서만 드러나는 문제 관찰용 | X |
| `docker-compose.certbot.yml` | 인증서 발급 시에만 임시 기동 | X |
| `docker-compose.pgadmin.yml` | pgAdmin GUI (opt-in). netlock 유지한 채 DB 접근 | X |

## CI/CD

### 파이프라인
- **CI** (`.github/workflows/ci.yml`) — PR 과 main push 마다 실행. 아래 게이트를 모두 통과해야 이미지가 push 된다
- **CD** (`.github/workflows/cd.yml`) — main push 시 self-hosted 러너에서 `deploy-rolling.ps1` 을 실행
- **배포 방식**: Self-hosted runner 사용
- **보안**: SSH(22) 포트는 닫고 GitHub Actions로만 배포
- **레지스트리**: GitHub Container Registry(ghcr.io, 비공개)
  (구 Docker Hub `para98` 는 침해 이력으로 폐기)

#### 이미지 잡의 스텝 순서
이미지 4개(backend · frontend · 프록시 2개)를 모두 같은 순서로 다룬다.

```
빌드 → Trivy 스캔 → push(:<commit-sha>) → 레지스트리 digest 캡처 → 아티팩트 업로드
```

- 스캔이 push **앞**이라, 수정 가능한 CRITICAL 이 있으면 이미지가 GHCR 에 올라가지 않는다.
  Trivy 는 빌드가 러너 로컬 데몬에 남긴 태그를 읽으므로 레지스트리 왕복이 필요 없다
- 이미지 잡은 시크릿 스캔·워크플로 린트·마이그레이션 검증이 **끝난 뒤에** 시작한다(`needs:`).
  없을 때는 병렬로 돌아서, 시크릿이 커밋에 있어도 이미지가 적재됐다

#### CI 가 CD 에 배포 대상을 알려주는 방법
CI 는 push 직후 각 이미지의 레지스트리 digest 를 캡처해 `image-digest-<이미지명>` 아티팩트로
올린다(`.github/scripts/capture-image-digest.sh`). CD 는 그 아티팩트를 받아
`ghcr.io/<owner>/<name>@sha256:...` 로 pull 한다 — **태그를 조립하지도, 태그를 digest 로
해석하지도 않는다.** 그래서 CI 가 스캔하고 E2E 가 검증한 그 바이트가 그대로 배포된다.

- 태그만 넘기면 태그→digest 해석이 CD 의 pull 시점에 일어나, CI 종료와 CD 의 pull 사이에
  같은 태그가 덮어써졌을 때 그 이미지가 배포된다. 왜 이 구조인지와 대안 비교는
  [ADR 0010](adr/0010-ci-hands-digest-to-cd.md)
- 아티팩트 보존기간(기본 90일)이 곧 "과거 릴리스로 되돌릴 수 있는 한계"다. 그보다 오래된
  릴리스는 revert 커밋 → CI → CD 로 되돌린다
- 수동 배포(`workflow_dispatch`)에는 `ci_run_id` 선택 입력이 있다. 비우면 main 의 최근 성공
  CI 를 조회하고 그 커밋이 HEAD 와 다르면 실패한다. 값을 넣으면 그 릴리스의 digest 로
  배포한다(롤백). 태그 폴백은 없다

### 품질 게이트
| 검사 | 막으려는 것 | 통과 기준 |
|---|---|---|
| 시크릿 스캔 (gitleaks) | 커밋에 들어간 자격증명 | 히스토리 전량 무탐지. 알려진 과거 탐지는 `.gitleaksignore` 에 사유와 함께 지문으로 등록 |
| 마이그레이션 검증 | 운영 DB 에서 처음 터지는 Flyway | 빈 PostgreSQL 15(운영과 같은 메이저)에 전량 적용 후 `flywayValidate` |
| 빌드 도구 무결성 | Gradle wrapper JAR 교체(공급망) | 배포 해시와 일치 |
| 테스트 실행 건수 | "빌드 성공"이 테스트 실행을 보장하지 않는 문제 | 결과 XML 의 실행 건수가 하한 이상 + Testcontainers 필수 클래스가 전부 실행됨 |
| 정적 분석 (SpotBugs) | 보안 결함, 그리고 나머지 지적의 증가 | 보안 범주 0건 + 총 지적이 베이스라인 이하 |
| 이미지 취약점 (Trivy) | 수정 가능한 CRITICAL | 이미지 4개(backend·frontend·프록시 2개) 각각 무탐지. push **앞**에서 돌아 불량 이미지는 GHCR 에 올라가지 않는다 |
| 액션 고정 | 서드파티 액션의 태그 이동 | 모든 `uses:` 가 커밋 SHA |

- 판정 스크립트는 `.github/scripts/` (`check-test-count.sh`, `check-spotbugs-security.sh`).
  하한값은 워크플로 env 와 `.github/spotbugs-baseline` 에 두고 코드에는 박지 않는다
- 워크플로 기본 토큰 권한은 `contents: read`. ghcr push 가 필요한 잡에서만 `packages: write` 로 올린다
- gitleaks 는 액션 대신 릴리스 바이너리를 **버전 고정 + SHA256 검증**으로 받는다 —
  원격 스크립트를 받아 바로 실행하지 않으면서 액션 의존성도 늘리지 않기 위해서다
- 시크릿 스캔은 커밋 훅(`.githooks/`)과 CI 양쪽에서 돈다. 훅은 로컬에서 우회될 수 있고
  CI 는 이미 커밋된 것만 보므로 한쪽만으로는 부족하다
- 의존성 업데이트는 Dependabot(`.github/dependabot.yml`)이 PR 로 올린다

### 차단과 경고를 나눈 이유
스타일·성능 지적까지 차단으로 두면 코드를 고치지 않았는데도 파이프라인이 멈추고, 그러면 결국
게이트 자체를 꺼버리게 된다. 그래서 차단 범위를 좁게 잡았다.

- **SpotBugs**: 보안 범주만 차단한다. MALICIOUS_CODE(대부분 가변 객체를 그대로 반환/보관하는
  캡슐화 지적)는 익스플로잇 가능한 결함이 아니라서 차단하지 않는다. 대신 총 지적 건수를
  베이스라인으로 동결해 **새로 늘어나는 것만** 막는다 — 기존 부채는 리포트에 남으니 줄여나갈 근거는 유지된다
- **Trivy**: `ignore-unfixed` 로 **고칠 수 있는** 취약점만 막는다. 패치가 없는 CVE 로 매번 실패하면 같은 결말이 된다
- **음성 결과를 통과 근거로 삼지 않는다**: SpotBugs 리포트가 없거나 분석 대상이 0개면 통과가 아니라 실패다.
  분석이 조용히 건너뛰어진 채 "지적 0건" 으로 통과한 적이 있어 넣은 검사다
- 베이스라인이나 테스트 하한을 올릴 때는 이유를 커밋 메시지에 남긴다

## SSL/TLS 설정

### Let's Encrypt
- **인증서**: 자동 갱신 설정
- **프로토콜**: TLSv1.2, TLSv1.3 지원
- **사이퍼 스위트**: 안전한 암호화 알고리즘 적용

### Nginx 설정
- **설정 파일**: `nginx/nginx.prod.conf`
- **보안 헤더**: HSTS, CSP, XFO, XCTO, Referrer-Policy, Permissions-Policy
- **레이트 리밋**: `limit_req` 모듈 적용
