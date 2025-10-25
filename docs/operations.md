# 운영 가이드

## 보안 설정

### 네트워크 보안
- **포트 포워딩**: 운영은 80/443만 포워딩
- **SSH 접근**: 22번 포트는 닫고 GitHub Actions self-hosted runner로만 배포
- **내부 서비스**: 백엔드(8090), 프론트(3000), DB(5432), Redis(6379)는 컨테이너 내부 통신만 사용

### 개발 환경 보안
- **DB/Redis 외부 노출 방지**: `docker-compose.yml`에서 `127.0.0.1:5432:5432`, `127.0.0.1:6379:6379`로 루프백 바인딩
- **모바일 테스트**: LAN에서만 필요 시 `3000/8090` 접근 허용 (방화벽 프라이빗만)
- **DB/Redis 보안**: 반드시 루프백 유지

### 보안 헤더
- **적용 파일**: `nginx/nginx.prod.conf`
- **헤더 목록**:
  - Strict-Transport-Security
  - Content-Security-Policy (CSP)
  - X-Frame-Options (XFO)
  - X-Content-Type-Options (XCTO)
  - Referrer-Policy
  - Permissions-Policy
  - limit_req (레이트 리밋)

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
- **방식**: `ENV_FILE`/Secrets 사용
- **보안**: 민감한 정보는 환경변수로 관리

## 모니터링

### 로그 관리
- **기본 설정**: 로그/모니터링 기본 설정 적용
- **로그 수집**: 애플리케이션 로그 수집 및 관리

### 성능 모니터링
- **Redis 캐시**: 성능 지표 모니터링
- **데이터베이스**: 쿼리 성능 및 연결 상태 모니터링
- **애플리케이션**: 응답 시간 및 에러율 모니터링
