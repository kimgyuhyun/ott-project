# OTT Project

Spring Boot 백엔드와 React 프론트엔드로 구성된 OTT 서비스 프로젝트입니다.

## 🚀 실행 방법

### 1. 로컬 개발 환경

#### 필수 요구사항
- Java 17+
- Node.js 18+
- Docker Desktop
- PostgreSQL (Docker로 실행 가능)

#### 단계별 실행

1. **데이터베이스 실행**
```bash
# PostgreSQL과 Redis를 Docker로 실행
npm run dev:db
# 또는
docker-compose up -d postgres redis
```

2. **백엔드 실행**
```bash
# 로컬에서 Spring Boot 실행
npm run dev:backend
# 또는
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
```

3. **프론트엔드 실행**
```bash
# React 개발 서버 실행
npm run dev:frontend
# 또는
cd frontend && npm start
```

4. **전체 서비스 한 번에 실행**
```bash
npm run dev:all
```

#### 로컬 개발 환경 접속 URL
- **프론트엔드**: http://localhost:3000
- **백엔드 API**: http://localhost:8090
- **Swagger UI**: http://localhost:8090/swagger-ui.html
- **PostgreSQL**: localhost:5432
- **Redis**: localhost:6379

### 2. Docker 환경 (배포용)

#### 전체 서비스 실행
```bash
# 프론트엔드 빌드 후 Docker 실행
npm run docker:up
# 또는
npm run build:frontend && docker-compose up -d
```

#### Docker 환경 접속 URL
- **메인 애플리케이션**: http://localhost
- **백엔드 API**: http://localhost/api/
- **Swagger UI**: http://localhost/swagger-ui/
- **API 문서**: http://localhost/api-docs/

#### Docker 관리 명령어
```bash
# 서비스 중지
npm run docker:down
# 또는
docker-compose down

# 로그 확인
npm run docker:logs
# 또는
docker-compose logs -f

# 특정 서비스만 실행
docker-compose up -d backend nginx
```

## 📁 프로젝트 구조

```
ott-project/
├── backend/                 # Spring Boot 백엔드
│   ├── src/main/java/
│   ├── src/main/resources/
│   └── build.gradle
├── frontend/               # React 프론트엔드
│   ├── src/
│   ├── public/
│   ├── build/             # 빌드된 정적 파일 (Docker에서 사용)
│   └── package.json
├── docker/                 # Docker 설정
│   ├── nginx/
│   └── postgres/
├── docker-compose.yml      # Docker Compose 설정
└── package.json           # 프로젝트 루트 스크립트
```

## 🔧 환경 설정

### 백엔드 설정
- **기본 설정**: `backend/src/main/resources/application.yml`
- **로컬 개발**: `dev` 프로파일 사용
- **Docker 환경**: `docker` 프로파일 사용

### 프론트엔드 설정
- **API 설정**: `frontend/src/config/api.ts`
- **환경별 자동 감지**: `NODE_ENV`에 따라 설정 변경

## 🛠️ 개발 도구

### 백엔드
- Spring Boot 3.x
- Spring Security
- Spring Data JPA
- PostgreSQL
- Redis
- Swagger/OpenAPI

### 프론트엔드
- React 18
- TypeScript
- Axios
- CSS Modules

### 인프라
- Docker
- Docker Compose
- Nginx (리버스 프록시 + 정적 파일 서빙)

## 📝 API 문서

- **Swagger UI**: http://localhost:8090/swagger-ui.html (로컬)
- **API 문서**: http://localhost/api-docs/ (Docker)

## 🔍 문제 해결

### Docker 연결 오류
```bash
# Docker Desktop이 실행 중인지 확인
docker ps

# Docker Desktop 재시작 후 다시 시도
```

### 포트 충돌
- 백엔드: 8090
- 프론트엔드: 3000 (로컬 개발)
- PostgreSQL: 5432
- Redis: 6379
- Nginx: 80

### 데이터베이스 연결 오류
```bash
# PostgreSQL 컨테이너 상태 확인
docker-compose ps postgres

# 로그 확인
docker-compose logs postgres
```

### 프론트엔드 빌드 오류
```bash
# 의존성 재설치
cd frontend && npm install

# 빌드 캐시 정리
cd frontend && npm run build -- --no-cache
```