# OTT Project

이 문서의 모든 경로·명령은 저장소 루트(`C:\solo-project\ott-project`) 기준이며, 세션도 이 폴더에서 연다.
상위 `solo-project/`에서 열면 `.claude/settings.json`이 로드되지 않아 훅이 전부 조용히 죽는다.

애니메이션 OTT 서비스(추천·소셜·정기결제·재생권한). Spring Boot 3.5(Java 21, JPA+MyBatis, Flyway)
+ Next.js 15(App Router, React Query) + PostgreSQL/Redis/Kafka(아웃박스)/RabbitMQ(던닝) + nginx.
단일 호스트 Docker Compose, GitHub Actions CI(ghcr push+Trivy)→CD(self-hosted 러너, 자동배포).

## 프로젝트 규칙
- 코드와 데이터 구조 규칙은 `C:\dev-standards\standards\ARCHITECTURE.md` 를 따른다.
- 배포, 보안, 파이프라인, 관측 규칙은 `C:\dev-standards\standards\PLATFORM.md` 를 따른다.
- 각 규칙은 [절대]와 [상황]으로 표시돼 있다. [절대]는 예외 없음. [상황]은 적용 조건과 미적용 조건이 함께 있으니, 미적용 조건에 해당하면 규칙을 어기는 것이 맞다.
- [상황] 규칙의 미적용 조건을 근거로 규칙을 어길 때는 그 이유를 코드 주석이나 커밋 메시지에 한 줄 남긴다.
- 규칙끼리 충돌하거나 판단이 서지 않으면 임의로 정하지 말고 물어본다.
- 규칙 문서는 이 저장소 밖 `C:\dev-standards` 에 있다. 저장소 안에 복사하지 않는다. 이 저장소는 공개이므로 규칙 문서 내용을 커밋하거나 README에 옮겨 적지 않는다(`.gitignore` 의 `standards/` 줄은 실수로 복사됐을 때를 막기 위해 남겨둔다).
- 이 프로젝트는 규칙 문서보다 먼저 만들어졌다. 기존 코드가 규칙과 다른 곳이 남아 있으므로, 주변 코드를 근거로 규칙을 판단하지 않는다.

### 언제 무엇을 읽는가
아래 작업을 시작하기 전에 해당 절을 먼저 읽는다. 기억에 의존해 규칙을 적용하지 않는다. `C:\dev-standards\standards\RATIONALE.md` 는 통독하지 않고 표에 적힌 절만 읽는다.

| 시작하는 작업 | 먼저 읽을 절 |
|---|---|
| 엔티티, DTO, Controller, Service 새로 만들기 | ARCHITECTURE 1, 2, 7 |
| 트랜잭션 경계 잡기, 데이터 접근 수단 고르기 | ARCHITECTURE 3, 4 |
| 인덱스 추가·삭제 | ARCHITECTURE 8, RATIONALE 3-1 — 실행 계획과 실측 시간을 전후로 캡처해야 한다. 나중에 만들 수 없으니 착수 전에 읽는다 |
| 재고·잔액·좌석·쿠폰 차감, 같은 행 동시 갱신 | ARCHITECTURE 9, RATIONALE 3-2 |
| 결제·주문, 외부 API 호출이 끼는 상태 전이 | ARCHITECTURE 5, 6, RATIONALE 3-3 |
| 목록·상세 조회 성능, 페이징, N+1, 커넥션 풀 | ARCHITECTURE 11, 12 |
| 캐시 추가 | ARCHITECTURE 10 |
| 메시지 발행·소비, 브로커 선택 | ARCHITECTURE 13 |
| 소비자를 별도 서비스로 분리, 데이터 망에 컨테이너 추가 | PLATFORM 3 — 브로커 인증을 걸 시점인지 판단한다 |
| 예외 처리와 에러 응답 | ARCHITECTURE 14 |
| 테스트 작성 | ARCHITECTURE 15, RATIONALE 3-6 — 계층이 아니라 로직으로 대상을 정한다. 짠 뒤에는 일부러 깨뜨려 빨간불이 나는지 확인한다 |
| 로그인, 인가, 쿠키, CORS, DB 계정 권한 | PLATFORM 4 |
| 사용자 입력 검증, 파일 업로드, 서버가 보내는 외부 요청 | PLATFORM 5 |
| compose, Dockerfile, nginx 설정 수정 | PLATFORM 2, 3 |
| 워크플로 수정, 의존성 추가 | PLATFORM 6, 7 |
| 마이그레이션 작성과 배포 | PLATFORM 8 — 파괴적 변경은 애플리케이션 배포와 같은 릴리스에 넣지 않는다 |
| 시크릿 추가·변경, 유출 대응 | PLATFORM 1 |
| 지표, 로그, 경보 추가 | PLATFORM 9 |
| 부하 테스트 | PLATFORM 10, RATIONALE 3-4 — 기준선을 먼저 측정하고 합격 기준을 테스트 전에 적는다 |
| 배포 후 보안 점검 | RATIONALE 3-5 |

## 폴더
- `backend/` Spring Boot (config/controller/dto/entity/repository/security/service 등 표준 레이어드)
- `frontend/` Next.js App Router, `src/lib/api/*` 도메인별 API 클라이언트
- `edge/` Cloudflare Worker (HLS 스트림 서명)
- `nginx/`, `monitoring/`, `pgadmin/`, `security/` 각 설정
- `docs/` deployment.md · messaging.md · operations.md · security.md · streaming.md · incident-2026-06.md (아래 참고, 내용 옮겨적지 말 것)
- `.deploy/`, `_incident_2026-06-20/` 과거 침해 사고 기록 — 참고용, 손대지 말 것

## 실행/배포
- 개발: `docker compose -f docker-compose.yml -f docker-compose.dev.yml up`
- 수동 프로덕션(단일 백엔드): `.\deploy.ps1`
- 수동 프로덕션(2인스턴스, 무중단, 통상 이 경로): `.\deploy-rolling.ps1`
- 실제 배포는 main push 시 CD(cd.yml)가 self-hosted 러너에서 `deploy-rolling.ps1`을 자동 실행 — 수동 배포는 로컬 확인/롤백용
- **절대 하면 안 됨**: 맨손 `docker compose up` (netlock 오버레이 없이 실행하면 프론트 아웃바운드가 열림 — 2026-06 XMRig 침해 원인)
- `.env`는 커밋되지 않음 — 배포 전 `.env.enc`를 SOPS+age로 복호화해야 함

## compose 파일 용도
- `docker-compose.yml` 베이스(공유 정의, 단독 실행 금지)
- `.prod.yml` 프로덕션 오버라이드 / `.dev.yml` 개발용(구 override.yml, 자동병합 방지 위해 개명)
- `.netlock.yml` 프론트 egress 차단 + 클린 이미지 고정 (프로덕션 필수)
- `.ha.yml` 백엔드 2인스턴스 오버레이 (prod+netlock 뒤에 붙여씀)
- `.monitoring.yml` Prometheus/Grafana/Loki (배포 스크립트에 항상 포함 — 빠지면 `--remove-orphans`가 지움)
- `.multi.yml` 다중 인스턴스 실험용 독립 스택(`-p ott-multi`로 분리 실행)
- `.pgadmin.yml`, `.certbot.yml` opt-in 유틸리티

## 함정
- `ott-app-2`(HA 2번째 인스턴스)가 떠 있으면 `deploy.ps1`은 실행을 거부하고 중단함(단일 인스턴스로 되돌리는 걸 막는 가드) — 통상은 `deploy-rolling.ps1` 사용. 의도적으로 단일 인스턴스로 롤백할 때만 `docker rm -f ott-app-2` 후 `deploy.ps1` 실행
- 무중단 배포는 인스턴스 2개만으로 안 됨: 동시 재기동 시 502 다수 발생(실측 164/197) — 반드시 순차 교체 스크립트 경유
- 롤링 배포 중 컬럼 DROP/RENAME 마이그레이션은 구버전 인스턴스를 깨뜨림 — expand/contract 패턴 사용, 테이블 추가·nullable 컬럼·DEFAULT 있는 NOT NULL은 안전
- postgres/redis는 `data` 네트워크에 격리되어 프론트에서 도달 불가해야 함 — 배포 스크립트가 자동 검증
- 카프카는 의도적으로 무인증(내부망 전용 결정, 문서화됨)
- `docs/` 문서는 최근 최신화됨 — 배포 절차 상세는 `docs/deployment.md`, 운영 체크리스트는 `docs/operations.md`, 보안 설계는 `docs/security.md`, 메시징(Kafka/RabbitMQ)은 `docs/messaging.md`, HLS 서명 재생은 `docs/streaming.md` 참고

## 탐색 제외
`node_modules/`, `.gradle/`, `build/`, `dist/`, `.next/`, `logs/`, 테스트 코드 전체
