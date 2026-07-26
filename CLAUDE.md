# OTT Project

이 문서의 모든 경로·명령은 `ott-project/` 기준(세션 작업 디렉터리가 상위 `solo-project/`일 수 있음).

애니메이션 OTT 서비스(추천·소셜·정기결제·재생권한). Spring Boot 3.5(Java 21, JPA+MyBatis, Flyway)
+ Next.js 15(App Router, React Query) + PostgreSQL/Redis/Kafka(아웃박스)/RabbitMQ(던닝) + nginx.
단일 호스트 Docker Compose, GitHub Actions CI(ghcr push+Trivy)→CD(self-hosted 러너, 자동배포).

## 폴더
- `backend/` Spring Boot (config/controller/dto/entity/repository/security/service 등 표준 레이어드)
- `frontend/` Next.js App Router, `src/lib/api/*` 도메인별 API 클라이언트
- `edge/` Cloudflare Worker (HLS 스트림 서명)
- `nginx/`, `monitoring/`, `pgadmin/`, `security/` 각 설정
- `docs/` deployment.md · messaging.md · operations.md · security.md (아래 참고, 내용 옮겨적지 말 것)
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
- `docs/` 4개 문서는 최근 최신화됨 — 배포 절차 상세는 `docs/deployment.md`, 운영 체크리스트는 `docs/operations.md`, 보안 설계는 `docs/security.md`, 메시징(Kafka/RabbitMQ)은 `docs/messaging.md` 참고

## 탐색 제외
`node_modules/`, `.gradle/`, `build/`, `dist/`, `.next/`, `logs/`, 테스트 코드 전체
