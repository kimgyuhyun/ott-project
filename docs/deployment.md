# 배포 가이드

## Docker 배포

### 컨테이너 구성
- **Nginx**: 리버스 프록시, SSL 터미네이션
- **Backend**: Spring Boot 애플리케이션 (포트 8090)
- **Frontend**: Next.js 애플리케이션 (포트 3000)
- **PostgreSQL**: 데이터베이스 (포트 5432)
- **Redis**: 캐시 서버 (포트 6379)

### 포트 설정
- **공개 포트**: 80 (HTTP), 443 (HTTPS)만 외부 노출
- **내부 통신**: 백엔드(8090), 프론트(3000), DB(5432), Redis(6379)는 컨테이너 내부 통신만 사용

### 실행 명령 (중요)
- **운영 배포(권장)**: `.\deploy.ps1` — 아래 3개 파일을 고정으로 묶어 egress 차단까지 적용
  - `docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.netlock.yml up -d`
- **개발**: `docker compose -f docker-compose.yml -f docker-compose.dev.yml up`
- **주의**: 플래그 없는 `docker compose up` 은 배포 명령이 아니다.
  netlock(egress 차단)이 빠져 프론트 인터넷 접근이 다시 열리므로 사용하지 말 것.
- **환경 변수**: `env.example` 참고. 실제 값은 `.env.enc`(SOPS+age)에서 복호화해 `.env` 로 주입.

## CI/CD

### GitHub Actions
- **배포 방식**: Self-hosted runner 사용
- **보안**: SSH(22) 포트는 닫고 GitHub Actions로만 배포
- **레지스트리**: GitHub Container Registry(ghcr.io, 비공개). CD가 커밋 이미지를 digest로 고정해 배포
  (구 Docker Hub `para98` 는 침해 이력으로 폐기)

## SSL/TLS 설정

### Let's Encrypt
- **인증서**: 자동 갱신 설정
- **프로토콜**: TLSv1.2, TLSv1.3 지원
- **사이퍼 스위트**: 안전한 암호화 알고리즘 적용

### Nginx 설정
- **설정 파일**: `nginx/nginx.prod.conf`
- **보안 헤더**: HSTS, CSP, XFO, XCTO, Referrer-Policy, Permissions-Policy
- **레이트 리밋**: `limit_req` 모듈 적용
