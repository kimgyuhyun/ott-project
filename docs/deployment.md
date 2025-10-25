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

### 환경 설정
- **개발 환경**: `docker-compose.yml` 사용
- **운영 환경**: `docker-compose.prod.yml` 사용
- **환경 변수**: `env.example` 참고

## CI/CD

### GitHub Actions
- **배포 방식**: Self-hosted runner 사용
- **보안**: SSH(22) 포트는 닫고 GitHub Actions로만 배포
- **Docker Hub**: 이미지 레지스트리로 사용

## SSL/TLS 설정

### Let's Encrypt
- **인증서**: 자동 갱신 설정
- **프로토콜**: TLSv1.2, TLSv1.3 지원
- **사이퍼 스위트**: 안전한 암호화 알고리즘 적용

### Nginx 설정
- **설정 파일**: `nginx/nginx.prod.conf`
- **보안 헤더**: HSTS, CSP, XFO, XCTO, Referrer-Policy, Permissions-Policy
- **레이트 리밋**: `limit_req` 모듈 적용
