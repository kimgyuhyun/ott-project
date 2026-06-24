# Build stage
FROM node:26-alpine AS builder
WORKDIR /app

# 패키지 파일 복사 및 의존성 설치 (캐싱 최적화)
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# 소스 코드 복사 및 빌드
COPY . .
RUN npm run build

# Development stage
FROM node:26-alpine AS development
WORKDIR /app

# 패키지 파일 복사 및 의존성 설치
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# 소스 코드는 볼륨 마운트로 처리 (docker-compose.override.yml에서)
EXPOSE 3000
CMD ["npm", "run", "dev"]

# Production stage
FROM node:26-alpine AS production
WORKDIR /app

ENV NODE_ENV=production
# standalone 출력은 실행에 필요한 최소 node_modules를 자체 포함하므로 별도 설치 불필요
# (불필요한 npm ci 제거 → 공격 표면/이미지 크기 감소)

# 빌드 산출물 복사 — 비root(node) 소유로 복사
COPY --from=builder --chown=node:node /app/.next/standalone ./
COPY --from=builder --chown=node:node /app/.next/static ./.next/static
COPY --from=builder --chown=node:node /app/public ./public

# 비root 사용자로 실행 (침해 시 root 권한 탈취 방지)
USER node

EXPOSE 3000
CMD ["node", "server.js"]
 