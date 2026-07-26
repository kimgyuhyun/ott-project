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
