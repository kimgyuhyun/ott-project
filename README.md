# OTT Project

사용자 취향을 분석해 콘텐츠를 추천하고,
리뷰·댓글·별점 등 소셜 기능과 멤버십/결제, 에피소드 감상기능을 
제공하는 애니메이션 OTT 서비스입니다. 

## Live
- 서비스: https://laputa.kozow.com

## 주요 특징

### 개인화 추천 시스템
> 사용자 데이터 분석 → 태그 가중치 계산 → 맞춤형 콘텐츠 제공

- **스마트 추천**: 사용자의 찜하기, 시청 기록, 평점 데이터를 분석하여 태그 가중치를 계산
- **실시간 캐싱**: Redis를 활용한 고성능 추천 결과 캐싱으로 빠른 응답 속도 제공
- **맞춤형 콘텐츠**: 상위 태그 기반 개인화 추천으로 사용자 취향에 맞는 작품 제공

### 소셜 기능
> 리뷰/댓글 시스템 + 평가 시스템 + 실시간 알림

- **커뮤니티**: 작품별 리뷰 작성 및 댓글/대댓글 시스템
- **상호작용**: 좋아요, 별점 평가를 통한 사용자 참여 유도
- **실시간 피드백**: 평균 별점 및 인기도 기반 작품 정보 제공

### 인증 & 보안
> OAuth2 소셜로그인 + 다층 보안 방어체계

- **소셜 로그인**: OAuth2 기반 구글/카카오/네이버 간편 로그인
- **보안 강화**: Nginx 리버스 프록시, HSTS, CSP 등 다층 보안 방어체계

### 결제 & 멤버십
> Iamport 결제 + 멤버십 등급 + 등급별 혜택

- **안전한 결제**: Iamport 연동을 통한 신뢰성 있는 결제 시스템
- **유연한 멤버십**: 다양한 등급별 혜택 및 관리 기능

### 성능 최적화
> Redis 캐싱 + 빠른 응답 + 향상된 사용자 경험

- **Redis 캐싱**: 최근 검색어, 평균 별점 등 자주 조회되는 데이터 캐싱
- **빠른 응답**: 캐시 전략을 통한 사용자 경험 향상

## 기술 스택

### Backend
![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green?style=flat-square&logo=spring)
![Spring Security](https://img.shields.io/badge/Spring%20Security-6.5-green?style=flat-square&logo=spring)
![JPA](https://img.shields.io/badge/JPA-3.5-blue?style=flat-square&logo=hibernate)
![MyBatis](https://img.shields.io/badge/MyBatis-3.0-red?style=flat-square&logo=mybatis)

### Frontend
![Next.js](https://img.shields.io/badge/Next.js-15-black?style=flat-square&logo=next.js)
![React](https://img.shields.io/badge/React-18-blue?style=flat-square&logo=react)
![TypeScript](https://img.shields.io/badge/TypeScript-5-blue?style=flat-square&logo=typescript)
![React Query](https://img.shields.io/badge/React%20Query-5.8-orange?style=flat-square&logo=react-query)

### Database & Cache
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue?style=flat-square&logo=postgresql)
![Redis](https://img.shields.io/badge/Redis-7-red?style=flat-square&logo=redis)

### Infrastructure
![Docker](https://img.shields.io/badge/Docker-Compose-blue?style=flat-square&logo=docker)
![Docker Hub](https://img.shields.io/badge/Docker%20Hub-Registry-blue?style=flat-square&logo=docker)
![Nginx](https://img.shields.io/badge/Nginx-alpine-green?style=flat-square&logo=nginx)
![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-CI%2FCD-black?style=flat-square&logo=github-actions)

## 주요 기능

### 개인화 추천
> 로그인 사용자: 개인화 추천 | 비로그인: 인기작 제공

- **스마트 추천**: 로그인 시 개인화 추천, 비로그인 시 인기작 제공
- **맞춤형 콘텐츠**: 사용자 취향 분석을 통한 정확한 추천 시스템

### 고급 플레이어
> HTML5 Video + HLS 스트리밍 + 편의 기능 + 사용자 설정

- **HTML5 Video**: 서명된 m3u8(HLS) 스트리밍 지원
- **편의 기능**: 
  - 다음 화 자동 재생, 이어보기(시청 기록 기반)
  - 오프닝/엔딩 스킵, 재생 속도/자막/화질 설정
  - 다음 화 오버레이

### 작품/에피소드 관리
> 상세 정보 + 메타데이터 + 사용자 활동 추적

- **상세 정보**: 작품/에피소드 상세, 주간 편성표
- **메타데이터**: 태그/장르/출연(성우 등) 정보
- **사용자 활동**: 좋아요/찜, 진행도(에피소드 시청 기록)

### 소셜 기능
> 리뷰/댓글 시스템 + 평가 시스템 + 실시간 알림

- **작품 리뷰**: 리뷰 작성/수정/삭제 + 리뷰에 댓글/대댓글, 좋아요
- **에피소드**: 댓글/대댓글, 좋아요
- **평가 시스템**: 별점 기록 및 평균 별점 표시, 알림(댓글/좋아요 등)

### 마이페이지
> 활동 관리 + 시청 기록 + 개인 통계 + 알림 관리

- **활동 관리**: 내가 쓴 리뷰/댓글, 좋아요/찜한 작품
- **시청 기록**: 에피소드 시청 진행도, 최근 본 애니, 정주행(완료/진행) 목록
- **활동 요약**: 내 별점/리뷰/댓글 활동 요약, 알림 확인

### 인증/인가
> OAuth2 소셜로그인 + 계정 관리 + 보안 제어

- **소셜 로그인**: OAuth2(구글/카카오/네이버) 로그인
- **계정 관리**: 이메일 인증/닉네임 설정, 접근 제어

### 결제/멤버십
> Iamport 결제 + 환불 정책 + 구독 관리 + 등급별 혜택

- **결제 시스템**: Iamport 연동 - 결제 생성/웹훅 수신, 결제/취소/환불 상태 동기화
- **환불 정책**: 24시간 내 환불, 시청 시간 기준 환불 정책 적용
- **구독 관리**: 구독 해지 - 말일 해지 예약 및 즉시 해지 분기, 멱등키 적용
- **등급별 혜택**: 멤버십 등급별 화질 제한, 전용 관리/가이드 페이지에서 멤버십 관리

### 검색/캐시
> 통합 검색 + Redis 캐싱 + 성능 최적화

- **통합 검색**: 제목/장르/태그/인물 통합 검색
- **성능 최적화**: 인기 검색어/평균 별점 Redis 캐시

## 개인 개발 범위 (Solo)
- 도메인 설계: 사용자/작품/에피소드/태그/리뷰/댓글/별점/알림/멤버십/결제/진행도
- 백엔드
  - Spring Security + OAuth2(구글/카카오/네이버), 세션/쿠키 도메인 구성
  - 개인화 추천 서비스(찜/시청/평점 → 태그 가중치 → Redis 캐시 → 상위 태그 추천)
  - 플레이백 인증(서명된 m3u8, 멤버십 등급별 화질 제한, 만료/서명 검증)
  - 결제 플로우: 결제 생성/검증, 웹훅 파싱/검증, 상태 전이(SUCCEEDED/FAILED/CANCELED/REFUNDED)
  - 환불 로직: 정책 검증(시간·시청 조건) 후 환불 처리, 결제/멤버십/이력 동기화
  - 구독 해지: 말일 해지 예약(cancelAtPeriodEnd) 및 즉시 해지 처리, 멱등성 보장
  - OpenAPI 문서화(springdoc), Flyway 마이그레이션, MyBatis 매퍼 + JPA 혼용, 테스트(통합/서비스)
- 프론트엔드
  - Next.js App Router, React Query 데이터 패칭/캐싱 구조
  - 플레이어 UI(이어보기, 다음 화 자동 재생, 스킵/자막/배속/화질)
  - 인증 흐름(UI/콜백), 댓글/리뷰/별점, 마이페이지/알림, 결제/멤버십 관리 UI
  - 검색/필터/정렬, 주간 편성/작품 상세/모달 UX
- 인프라
  - Nginx 리버스 프록시/secure_link, Docker 이미지/Compose, Docker Hub Registry, GitHub Actions CD
  - 환경변수/비밀키 관리(`ENV_FILE`/Secrets), 로그/모니터링 기본 설정

## 데이터베이스 개요
- 사용자/작품/에피소드/태그/리뷰/댓글/별점/팔로우/결제 등
- Redis: 네임스페이스 `ott`, 최근 검색어 TTL 설정

## 환경 변수 참고
- 예시 파일: `env.example`
- 주요 항목: DB(PostgreSQL), Redis, OAuth2(구글/카카오/네이버), TMDB, BASE_URL/COOKIE_DOMAIN, Iamport 등

## 외부 연동
- **콘텐츠/메타데이터**: TMDB, Jikan
- **결제**: Iamport (카카오/토스/나이스 채널 키)
- **소셜 로그인**: Google, Kakao, Naver

## 문서

- [배포 가이드](docs/deployment.md) - Docker 배포, CI/CD, SSL 설정
- [운영 가이드](docs/operations.md) - 보안 설정, 환경 변수, 모니터링
