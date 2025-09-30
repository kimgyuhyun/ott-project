# OTT Project

사용자 취향을 분석해 개인 맞춤형 콘텐츠를 추천하고,
리뷰·댓글·별점 등 소셜 기능과 멤버십/결제, 에피소드 감상기능을 
제공하는 애니메이션 OTT 서비스입니다. 

## Live
- 서비스: https://laputa.kozow.com

## 소개
- 개인화 추천: 찜/시청/평점 기반 태그 가중치 계산 → Redis 캐시 → 상위 태그 기반 추천
- 소셜 기능: 리뷰/댓글/대댓글/좋아요/별점
- OAuth2 소셜 로그인(구글/카카오/네이버)
- 멤버십/결제(Iamport)
- 최근 검색어/평균 별점 등 Redis 캐시

## 기술 스택
- Backend: Java 21, Spring Boot, JPA, Spring Security, OAuth2, Redis, MyBatis
- DB/Cache: PostgreSQL, Redis
- Frontend: Next.js 15, React 18, React Query, TypeScript
- Infra: Nginx, Docker, GitHub Actions, Docker Hub Registry
- 외부 API: TMDB, Jikan, Iamport

## 주요 기능
- 개인화 추천 제공 (로그인 시 개인화, 비로그인 시 인기작). 자세한 API는 Swagger 참조.
- HTML5 video 기반 플레이어 + 서명된 m3u8(HLS) 스트리밍
  - 다음 화 자동 재생, 이어보기(시청 기록 기반), 오프닝/엔딩 스킵
  - 재생 속도/자막/화질 설정, 다음 화 오버레이
- 작품/에피소드
  - 작품/에피소드 상세, 주간 편성표, 태그/장르/출연(성우 등) 정보
  - 좋아요/찜, 진행도(에피소드 시청 기록)
- 소셜
  - 작품 리뷰: 리뷰 작성/수정/삭제 + 리뷰에 댓글/대댓글, 좋아요
  - 에피소드: 댓글/대댓글, 좋아요
  - 별점 기록 및 평균 별점 표시, 알림(댓글/좋아요 등)
- 마이페이지
  - 내가 쓴 리뷰/댓글, 좋아요/찜한 작품
  - 에피소드 시청 진행도, 최근 본 애니, 정주행(완료/진행) 목록
  - 내 별점/리뷰/댓글 활동 요약, 알림 확인
- 인증/인가
  - OAuth2(구글/카카오/네이버) 로그인, 이메일 인증/닉네임 설정, 접근 제어
- 결제/멤버십
  - Iamport 연동: 결제 생성/웹훅 수신, 결제/취소/환불 상태 동기화
  - 환불/취소 정책 반영(예: 24시간·시청 시간 기준), 환불 금액/시각 기록
  - 구독 해지: 말일 해지 예약 및 즉시 해지 분기, 멱등키 적용
  - 멤버십 등급별 화질 제한, 전용 관리/가이드 페이지에서 멤버십 관리
- 검색/캐시
  - 통합 검색(제목/장르/태그/인물), 인기 검색어/평균 별점 Redis 캐시

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

## 외부 연동
- 콘텐츠/메타데이터: TMDB, Jikan
- 결제: Iamport (카카오/토스/나이스 채널 키)
- 소셜 로그인: Google, Kakao, Naver

## 데이터베이스 개요
- 사용자/작품/에피소드/태그/리뷰/댓글/별점/팔로우/결제 등
- Redis: 네임스페이스 `ott`, 최근 검색어 TTL 설정

## 환경 변수 참고
- 예시 파일: `env.example`
- 주요 항목: DB(PostgreSQL), Redis, OAuth2(구글/카카오/네이버), TMDB, BASE_URL/COOKIE_DOMAIN, Iamport 등

## Port Forwarding & Security (운영 가이드)
- 포트 포워딩(라우터): 운영은 80/443만 포워딩 권장. 22(SSH)는 배포/점검 시에만 임시 오픈.
- Nginx 공개 포트: `docker-compose.prod.yml`에서 `nginx`만 `80:80`, `443:443` 노출.
- 내부 서비스: 백엔드(8090), 프론트(3000), DB(5432), Redis(6379)는 컨테이너 내부 통신만 사용.
  - dev 로컬에서 DB/Redis 외부 노출 방지: `docker-compose.yml`은 `127.0.0.1:5432:5432`, `127.0.0.1:6379:6379`로 루프백 바인딩.
- 보안 헤더/HSTS/레이트리밋: `nginx/nginx.prod.conf`에 적용(Strict-Transport-Security, CSP, XFO, XCTO, Referrer-Policy, Permissions-Policy, limit_req 등).
- 모바일 테스트: LAN에서만 필요 시 `3000/8090` 접근 허용(방화벽 프라이빗만). DB/Redis는 반드시 루프백 유지.

## 라이선스
MIT
