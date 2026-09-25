# OTT Project

이 문서의 모든 경로·명령은 저장소 루트(`C:\solo-project\ott-project`) 기준이며, 세션도 이 폴더에서 연다.
상위 `solo-project/`에서 열면 `.claude/settings.json`이 로드되지 않아 훅이 전부 조용히 죽는다.

애니메이션 OTT 서비스(추천·소셜·정기결제·재생권한). Spring Boot 3.5(Java 21, JPA+MyBatis, Flyway)
+ Next.js 15(App Router, React Query) + PostgreSQL/Redis/Kafka(아웃박스)/RabbitMQ(던닝) + nginx.
단일 호스트 Docker Compose, GitHub Actions CI(ghcr push+Trivy)→CD(self-hosted 러너, 자동배포).

## 프로젝트 규칙

@C:/dev-standards/templates/CLAUDE-common.md

- 위 공통 규칙 블록(규칙 문서 경로, 표시 읽는 법, "언제 무엇을 읽는가" 표)이 보이지 않으면 작업 전에 `C:\dev-standards\templates\CLAUDE-common.md` 를 직접 읽는다.
- 이 저장소는 공개이므로 규칙 문서 내용을 커밋하거나 README에 옮겨 적지 않는다(`.gitignore` 의 `standards/` 줄은 실수로 복사됐을 때를 막기 위해 남겨둔다).
- 이 프로젝트는 규칙 문서보다 먼저 만들어졌다. 기존 코드가 규칙과 다른 곳이 남아 있으므로, 주변 코드를 근거로 규칙을 판단하지 않는다.
- 새 기기에서는 클론 직후 `docs/setup.md` 를 먼저 한다(스킬 정션 포함).

### 프로젝트 전제

PLATFORM 0절의 항목이다. 규칙이 이 값에 따라 갈리므로 비워두지 않는다. 값이 바뀌는 변경은 이 표를 먼저 고친 뒤 시작한다.

| 항목 | 값 |
|---|---|
| 앱 인스턴스 수 | 2 (`docker-compose.ha.yml` 의 ott-app·ott-app-2, `deploy-rolling.ps1` 이 하나씩 교체). DB 커넥션 상한 = Hikari 20 × 2 = 40 |
| 프론트엔드 형태 | SSR 컨테이너, 같은 호스트 (Next.js standalone `node server.js`, `default` 망에만 붙고 nginx 가 3000 으로 프록시) |
| 사이트 경계 | 공용 무료 도메인 (`laputa.kozow.com`, 프론트와 API 가 같은 오리진). SameSite 를 방어로 세지 않고 `OriginValidationFilter` 의 출처 검증을 반드시 둔다 |
| 인증 방식 | 서버 세션 + 세션 쿠키 (Spring Session Redis, JSESSIONID `Secure; HttpOnly; SameSite=Lax`). 이메일·비밀번호 로그인과 소셜(Google·Kakao·Naver). CSRF 토큰은 기본 꺼짐, `OriginValidationFilter` 로 출처 검증 |
| 결제 형태 | 정기(빌링키 `customer_uid`, 아임포트 V1 `api.iamport.kr`), 웹훅 수신 `/api/payments/webhook` |
| Runner 위치 | CD 는 프로덕션 호스트와 같은 머신 (Windows, Docker Desktop, `cd.yml` self-hosted 가 `AGE_KEY` 로 `.env` 복호화). CI 빌드·스캔·push 는 GitHub 호스팅 |
| 엣지 프록시 | 없음 (Cloudflare 는 R2·Worker·Turnstile 용도). Docker Desktop SNAT 때문에 nginx `$remote_addr` 가 브리지 게이트웨이 하나로 모여, 속도 제한은 클라이언트별이 아니라 전체 총량으로만 동작 |
| 실사용자와 개인정보 | 없음(포트폴리오, 테스트 계정). 저장 항목: 이메일·이름·비밀번호(BCrypt)·프로필 이미지 URL·소셜 providerId(`User`), 로그인 시도 이메일·IP·User-Agent(`AuthEvent`), 카드 브랜드·끝 4자리·만료 월/연·빌링키 식별자(`PaymentMethod`) |
| Redis 역할 | 세션 + 캐시 + 분산 락(ShedLock) + DB 에 아직 안 내려간 시청 진행률 버퍼 + 로그인 실패 카운터·메일 인증 코드. 영속화 꺼짐(`--save "" --appendonly no`), 재시작하면 전원 로그아웃되고 미반영 진행률이 사라진다 |
| DB 엔진 | PostgreSQL |
| 메시지 브로커 | Kafka(아웃박스 이벤트 발행) + RabbitMQ(정기결제 던닝의 지연 재시도). 두 브로커를 함께 쓰므로 ARCHITECTURE 13절의 역할 분리 규칙이 걸린다 |
| 환경 구성 | 로컬 + 프로덕션. 상시 검증 환경은 없다. E2E(`docker-compose.e2e.yml`)와 복구 점검(`docker-compose.restore-test.yml`)은 필요할 때 임시 스택으로 띄운다 |
| 가상 스레드 | 사용 안 함 (`spring.threads.virtual.enabled` 설정 없음) |

## 폴더
- `backend/` Spring Boot (config/controller/dto/entity/repository/security/service 등 표준 레이어드)
- `frontend/` Next.js App Router, `src/lib/api/*` 도메인별 API 클라이언트
- `edge/` Cloudflare Worker (HLS 스트림 서명)
- `nginx/`, `monitoring/`, `pgadmin/`, `security/` 각 설정
- `docs/` setup.md(새 기기 세팅) · deployment.md · messaging.md · operations.md · security.md · streaming.md · restore-runbook.md · incident-2026-06.md · adr/ (아래 참고, 내용 옮겨적지 말 것)
- `.deploy/`, `_incident_2026-06-20/` 과거 침해 사고 기록 — 참고용, 손대지 말 것

## 실행/배포
- 개발: `docker compose -f docker-compose.yml -f docker-compose.dev.yml up`
- 수동 프로덕션(단일 백엔드): `.\deploy.ps1`
- 수동 프로덕션(2인스턴스, 무중단, 통상 이 경로): `.\deploy-rolling.ps1`
- 실제 배포는 main push 시 CD(cd.yml)가 self-hosted 러너에서 `deploy-rolling.ps1`을 자동 실행 — 수동 배포는 로컬 확인/롤백용
- **절대 하면 안 됨**: 맨손 `docker compose up` (netlock 오버레이 없이 실행하면 프론트 아웃바운드가 열림 — 2026-06 XMRig 침해 원인)
- `.env`는 커밋되지 않음 — 배포 전 `.env.enc`를 SOPS+age로 복호화해야 함
- compose 파일별 용도와 조합은 `docs/deployment.md` 의 "compose 파일 구성" 표

## 함정
- `ott-app-2`(HA 2번째 인스턴스)가 떠 있으면 `deploy.ps1`은 실행을 거부하고 중단함(단일 인스턴스로 되돌리는 걸 막는 가드) — 통상은 `deploy-rolling.ps1` 사용. 의도적으로 단일 인스턴스로 롤백할 때만 `docker rm -f ott-app-2` 후 `deploy.ps1` 실행
- 무중단 배포는 인스턴스 2개만으로 안 됨: 동시 재기동 시 502 다수 발생(실측 164/197) — 반드시 순차 교체 스크립트 경유
- 롤링 배포 중 컬럼 DROP/RENAME 마이그레이션은 구버전 인스턴스를 깨뜨림 — expand/contract 패턴 사용, 테이블 추가·nullable 컬럼·DEFAULT 있는 NOT NULL은 안전
- postgres/redis는 `data` 네트워크에 격리되어 프론트에서 도달 불가해야 함 — 배포 스크립트가 자동 검증
- 카프카는 의도적으로 무인증(내부망 전용 결정, 문서화됨)
- DB 를 백업에서 되살리는 절차는 `docs/restore-runbook.md` — 평상시 점검(`restore-drill.ps1 -Mode Check`)과 실제 복구가 함께 있다. 복원은 globals(롤)를 데이터베이스 덤프보다 **먼저** 적용해야 한다
- 구조를 **왜** 그렇게 골랐는지는 `docs/adr/` — 결정 하나당 파일 하나. 기존 결정을 뒤집을 때는 파일을 고치지 말고 새 ADR 을 쓰고 이전 것을 `대체됨` 으로 바꾼다

## 탐색 제외
`node_modules/`, `.gradle/`, `build/`, `dist/`, `.next/`, `logs/`, 테스트 코드 전체
