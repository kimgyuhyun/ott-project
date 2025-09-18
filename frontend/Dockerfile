# Build stage
FROM node:20-alpine AS builder
WORKDIR /app

# 패키지 파일 복사 및 의존성 설치 (캐싱 최적화)
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# 소스 코드 복사 및 빌드
COPY . .
RUN npm run build

# Development stage
FROM node:20-alpine AS development
WORKDIR /app

# 패키지 파일 복사 및 의존성 설치
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# 소스 코드는 볼륨 마운트로 처리 (docker-compose.override.yml에서)
EXPOSE 3000
CMD ["npm", "run", "dev"]

# Production stage
FROM node:20-alpine AS production
WORKDIR /app

# 프로덕션 의존성만 설치
COPY package.json package-lock.json ./
RUN npm ci --only=production --no-audit --no-fund

# 빌드된 애플리케이션 복사
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public

EXPOSE 3000
CMD ["node", "server.js"]
 