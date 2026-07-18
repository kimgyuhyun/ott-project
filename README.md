# OTT Project — 애니메이션 OTT 서비스

사용자 취향을 분석해 콘텐츠를 추천하고, 리뷰·댓글·별점 등 소셜 기능과
멤버십/결제, 에피소드 감상 기능을 제공하는 **애니메이션 OTT 서비스**입니다.
개인화 추천, 정기결제/환불, 재생 권한 제어 등 OTT 핵심 흐름을 다룬 개인 프로젝트입니다.

> 🌐 서비스 주소: **https://laputa.kozow.com** (단일 호스트 Docker Compose 배포)

---

## 주요 기능

| 영역 | 기능 |
|------|------|
| **개인화 추천** | 찜/시청/평점 → 태그 가중치 → 상위 태그 기반 추천 (로그인), 비로그인은 인기작 · 24h 트렌드 |
| **고급 플레이어** | HTML5 Video + 서명 스트림 URL, 이어보기 · 다음 화 자동재생 · OP/ED 스킵 · 배속/자막/화질 |
| **작품/에피소드** | 상세 · 주간 편성표, 태그/장르/성우 메타데이터, 좋아요/찜/시청 진행도 |
| **소셜** | 작품 리뷰 + 리뷰 댓글/대댓글, 에피소드 댓글, 좋아요, 별점, 알림 |
| **마이페이지** | 내 리뷰/댓글, 찜/좋아요, 시청 기록 · 정주행 진행, 활동 요약 |
| **인증/인가** | OAuth2(구글/카카오/네이버) + 이메일 인증, 세션/쿠키 기반 접근 제어 |
| **결제/멤버십** | Iamport 연동 결제/웹훅/환불, 등급별 화질 제한, 구독 해지/재개 · 플랜 변경 |
| **정기결제** | 저장 결제수단 자동 청구, 실패 시 지연 재시도(던닝) · 자동 해지 <sup>[※](#3-정기결제-실패-던닝-rabbitmq-ttl--dlx)</sup> |
| **검색** | 제목/장르/태그/인물 통합 검색 + 자동완성, 최근 검색어 |

---

## 기술 스택

### Backend
![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green?style=flat-square&logo=spring)
![Spring Security](https://img.shields.io/badge/Spring%20Security-6.5-green?style=flat-square&logo=spring)
![JPA](https://img.shields.io/badge/JPA-3.5-blue?style=flat-square&logo=hibernate)
![MyBatis](https://img.shields.io/badge/MyBatis-3.0-red?style=flat-square&logo=mybatis)
![Flyway](https://img.shields.io/badge/Flyway-migration-red?style=flat-square&logo=flyway)

- **Spring Data JPA** (쓰기/도메인) + **MyBatis** (복잡 조회/통계) 혼용
- **Flyway** 스키마 마이그레이션, **springdoc** OpenAPI 문서화

### Frontend
![Next.js](https://img.shields.io/badge/Next.js-15-black?style=flat-square&logo=next.js)
![React](https://img.shields.io/badge/React-18-blue?style=flat-square&logo=react)
![TypeScript](https://img.shields.io/badge/TypeScript-5-blue?style=flat-square&logo=typescript)
![React Query](https://img.shields.io/badge/React%20Query-5.8-orange?style=flat-square&logo=react-query)

- **Next.js App Router**, **React Query** 서버 상태/캐싱, 도메인별 `lib/api/*` 클라이언트

### Data / Messaging
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue?style=flat-square&logo=postgresql)
![Redis](https://img.shields.io/badge/Redis-7-red?style=flat-square&logo=redis)
![Kafka](https://img.shields.io/badge/Kafka-3.7%20KRaft-black?style=flat-square&logo=apachekafka)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.13-orange?style=flat-square&logo=rabbitmq)

- **PostgreSQL** 주 저장소, **Redis** 캐시/추천/트렌드
- **Kafka** — 결제 성공 부수효과(영수증 등) 이벤트 스트림 (Outbox 패턴)
- **RabbitMQ** — 정기결제 실패 재시도 지연 큐 (TTL + DLX)

### Infra / DevOps
![Docker](https://img.shields.io/badge/Docker-Compose-blue?style=flat-square&logo=docker)
![Docker Hub](https://img.shields.io/badge/Docker%20Hub-Registry-blue?style=flat-square&logo=docker)
![Nginx](https://img.shields.io/badge/Nginx-reverse%20proxy-green?style=flat-square&logo=nginx)
![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-CD-black?style=flat-square&logo=github-actions)

- **nginx** 리버스 프록시(HTTPS · secure_link), **Docker Compose** 단일 호스트 구성
- **GitHub Actions** → **Docker Hub** 이미지 빌드/푸시 후 서버 배포
- 보안 하드닝: 앱/프론트/브로커는 **루프백 전용 바인딩**, 외부 진입점은 nginx(80/443)뿐

---

## 아키텍처

```
                        ┌──────────────────────────────────────────┐
   Browser  ──HTTPS──►  │  nginx (edge, :80/:443)                   │
                        │    /api/*  → backend                      │
                        │    /*      → frontend (Next.js)           │
                        └──────────┬────────────────┬───────────────┘
                                   │                │
                       ┌───────────▼──────┐   ┌─────▼─────────────┐
                       │ backend (:8090)  │   │ frontend (:3000)  │
                       │ Spring Boot      │   │ Next.js App Router│
                       └──┬───┬───┬───┬───┘   └───────────────────┘
                          │   │   │   │
             ┌────────────┘   │   │   └──────────────┐
             ▼                ▼   ▼                  ▼
     ┌──────────────┐  ┌──────────┐ ┌───────────┐ ┌──────────────┐
     │ PostgreSQL   │  │ Redis 7  │ │  Kafka    │ │  RabbitMQ    │
     │ (JPA/Flyway) │  │ cache/rec│ │ payment   │ │ billing retry│
     │              │  │          │ │ side-fx   │ │ (TTL + DLX)  │
     └──────────────┘  └──────────┘ └───────────┘ └──────────────┘

     외부 연동: Iamport(결제) · TMDB/Jikan(메타데이터) · Google/Kakao/Naver(OAuth2)
```

### 계층 구조 (backend)

```
com.ottproject.ottbackend
├── controller   REST 엔드포인트 (@RestController)
├── service      비즈니스 로직 + 결제/추천/정기결제/이벤트 컨슈머
├── entity       JPA 엔티티
├── dto          요청/응답 DTO
├── enums        도메인 enum (결제/구독 상태 등)
├── repository   Spring Data JPA 리포지토리
├── mybatis      MyBatis 매퍼 (복잡 조회/통계)
├── mappers      매퍼 인터페이스
├── event        도메인 이벤트
├── config       Security · Redis · Kafka · Rabbit · OpenAPI 설정
├── handler      OAuth2 성공/실패 핸들러
├── security     인증 필터/헬퍼
├── util         HLS 서명 · 보안 유틸
└── validation   커스텀 검증 애노테이션
```

---

## 핵심 설계

### 1. 개인화 추천 파이프라인 (Redis)
사용자 활동을 **행동별 가중치**로 환산해 태그 선호도를 만들고, 상위 태그로 후보를 추립니다.

1. **찜 ×3.0 · 시청 ×2.0 · 고평점(4.0↑) ×4.0** 으로 태그별 가중치를 누적 → Redis ZSet(TTL 1h)
2. 상위 3개 태그로 후보 조회 → **이미 본 작품 제외** → `Σ태그가중치 + 평점×0.1` 로 재정렬
3. 최종 추천 목록을 Redis 캐시(TTL 30m), 찜/시청/평점 변경 시 **캐시 무효화**
4. 태그 선호도가 없으면(신규 유저) **인기작 폴백**, 예외 시에도 안전하게 폴백

> 별도 트렌드 집계(`trend:24h` ZSet)로 실시간 인기작도 제공.

### 2. 결제 부수효과: Outbox + Kafka
결제 확정(정합성)과 부수효과(영수증 메일 등)를 분리해 **메일 서버 장애가 결제에 영향을 주지 않도록** 했습니다.

- **결제 확정(동기)** — PG 재검증 → 상태 확정 → 구독 생성/플랜 변경을 한 트랜잭션에서 원자 처리하고, **클라 확정·웹훅·재조정 배치의 3중 확인**으로 정합성 수렴
- **Outbox 패턴** — 결제 확정 트랜잭션과 같은 커밋으로 아웃박스 행을 남기고, 스케줄러가 폴링해 Kafka로 발행 → **dual-write 유실 방지**
- 메시지 키 = `aggregateId`(결제 PK)로 **동일 애그리거트 파티션 순서 보장**
- 컨슈머는 `eventId` 기준 **Redis 멱등 처리**(중복 배달 방어, TTL 7일), 실패 시 재시도 → DLT 격리
- 발행은 동기 확인(`.get()`) 후에만 `PUBLISHED` 마킹 → **at-least-once**

### 3. 정기결제 실패 던닝: RabbitMQ TTL + DLX
결제 실패 시 **폴링 없이 "실패 시점 + 지연"에 정확히 도착하는 건별 재시도**를 구현했습니다.

- 플러그인 없이 표준 기능만: 소비자 없는 **대기 큐에 TTL**을 걸고, 만료 메시지가 **DLX**를 타고 작업 큐로 이동
- 1차 실패 → 3h 대기, 2차 실패 → 24h 대기 (지연별로 큐를 분리해 head-of-line blocking 회피)
- 3회 소진 시 **자동 해지 + 안내 메일**, 성공 시 기간 연장 · 재시도 카운트 리셋
- **안전망**: 브로커 장애로 발행 실패하면 스케줄 스윕 배치(`nextBillingAt`)로 폴백 → 메시지 유실에도 결제 누락 없음
- 소비 시점 **스테일 메시지 가드**(이미 복구/해지됐거나 스윕이 먼저 처리한 건은 skip)

> ※ *현재 배포는 카카오페이 **원타임 테스트 채널**(TC0ONETIME)이라 빌링키(`customer_uid`) 발급이 불가능해 실제 자동 청구는 항상 실패한다. 즉 **던닝 경로(재시도 → 자동 해지)가 동작하는 것까지가 데모 범위**이며, 청구 성공 경로는 실환경에서 재현되지 않는다. 정기결제 지원 채널로 전환하면 빌링키 등록만 추가하면 된다.*

### 4. 재생 권한 & 서명 URL
접근 제어를 **URL 발급 게이트**에 집중시켰습니다.

- `canStream` — 미로그인 차단, 비활성/미공개 차단, **1~3화 무료 · 4화↑ 멤버십 필요**를 실시간 멤버십 상태로 판정
- 발급 시 멤버십 등급에 따라 **화질 제한**(비회원 → 720p 마스터로 치환)
- nginx `secure_link` 서명(`e`/`st`, TTL 10m)을 부착 — *현재 배포는 공개 테스트 MP4라 서명 검증 오리진이 없어 **설계 스텁**이며, 접근 제어는 `canStream` 게이트가 담당. 실제 HLS 오리진 도입 시 동일 시크릿으로 `secure_link_md5`만 켜면 코드 변경 없이 효력 발생.*

### 5. 구독 라이프사이클
- **말일 해지 예약**(`cancelAtPeriodEnd`)과 즉시 해지를 분기, 해지/재개에 **멱등키**로 중복 방지
- **플랜 변경 예약** — 다음 결제일에 반영, 정기결제 배치가 예약분을 함께 처리
- 결제 상태 전이(`SUCCEEDED`/`FAILED`/`CANCELED`/`REFUNDED`)와 멤버십/이력 동기화
- **환불 정책** — 24시간 내 · 시청 시간 기준 검증 후 처리

### 6. Redis 캐싱 전략
추천 결과 · 태그 선호도 · 시청 집합 · 24h 트렌드 · 인기 검색어/평균 별점 등 **자주 조회·재계산 비용이 큰 데이터**를 네임스페이스(`ott`) + TTL로 캐싱해 응답 지연과 DB 부하를 낮춥니다.

---

## 프로젝트 구조

```
ott-project/
├── backend/                  Spring Boot 애플리케이션
│   └── src/main/java/com/ottproject/ottbackend/
│       └── (controller / service / entity / dto / config / mybatis ...)
├── frontend/                 Next.js App Router
│   └── src/
│       ├── app/              App Router 페이지
│       ├── components/       anime · auth · episode · home · membership
│       │                     · player · reviews · search · layout · ui
│       ├── hooks/            useAuth · usePayment · useProrationPayment
│       └── lib/              api/* · AuthContext · config
├── nginx/                    리버스 프록시 설정(HTTPS · secure_link)
├── docs/                     배포 · 운영 문서
├── docker-compose.yml        postgres · redis · kafka · rabbitmq · app · frontend · nginx
└── env.example               환경 변수 예시
```

---

## 주요 API

모든 엔드포인트는 `/api` 프리픽스를 사용합니다.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/api/auth/register` · `/api/auth/login` | 이메일 회원가입 · 로그인(인증) |
| `GET`  | `/api/oauth2/...` | OAuth2 소셜 로그인(구글/카카오/네이버) |
| `GET`  | `/api/anime` · `/api/anime/{aniId}` | 작품 목록(필터/정렬) · 상세 |
| `GET`  | `/api/anime/recommended` | 개인화 추천(로그인) / 인기작(비로그인) |
| `GET`  | `/api/anime/popular` · `/api/anime/trending-24h` · `/api/anime/weekly/{day}` | 인기작 · 24h 트렌드 · 주간 편성 |
| `GET`  | `/api/episodes/{id}/stream-url` | 재생 권한 검사 후 서명 스트림 URL 발급 |
| `GET`  | `/api/player/episodes/{id}/subtitles` · `/skips` · `/users/me/settings` | 자막 · OP/ED 스킵 · 재생 설정 |
| `GET`  | `/api/search` · `/api/search/suggest` · `/api/search/recent` | 통합 검색 · 자동완성 · 최근 검색어 |
| `GET`/`POST` | `/api/anime/{aniId}/reviews` · `/ratings` (+ `/comments`) | 리뷰 · 별점 · 리뷰 댓글 |
| `GET`  | `/api/memberships/plans` · `/api/users/me/membership` | 멤버십 플랜 · 내 멤버십 |
| `POST` | `/api/memberships/subscribe` · `/cancel` · `/resume` | 구독 신청 · 말일 해지 예약 · 재개 |
| `PUT`  | `/api/memberships/change-plan` | 플랜 변경(예약) |
| `POST` | `/api/payments/checkout` · `/api/payments/{id}/complete` | 결제 생성 · 확정 |
| `POST` | `/api/payments/webhook` · `/api/payments/{id}/refund` | 결제 웹훅 수신 · 환불 |
| `GET`/`POST`/`PUT`/`DELETE` | `/api/payment-methods` (+ `/{id}/default`) | 저장 결제수단 CRUD · 기본 지정 |
| `GET`  | `/api/notifications` · `/api/mypage` | 알림 · 마이페이지 |

---

## 개인 개발 범위 (Solo)

- **도메인 설계**: 사용자/작품/에피소드/태그/리뷰/댓글/별점/알림/멤버십/결제/진행도
- **백엔드**
  - Spring Security + OAuth2(구글/카카오/네이버), 세션/쿠키 도메인 구성
  - 개인화 추천(찜/시청/평점 → 태그 가중치 → Redis → 상위 태그 추천)
  - 결제 플로우: 생성/검증, 웹훅 파싱/검증, 상태 전이, 환불(정책 검증) · 구독 해지(멱등키)
  - Outbox + Kafka 결제 부수효과 · RabbitMQ TTL/DLX 정기결제 던닝
  - 재생 권한(멤버십 등급별 화질 제한, secure_link 서명 설계)
  - OpenAPI 문서화, Flyway 마이그레이션, MyBatis + JPA 혼용, 통합/서비스 테스트
- **프론트엔드**
  - Next.js App Router, React Query 데이터 패칭/캐싱
  - 플레이어 UI(이어보기 · 다음 화 자동재생 · 스킵/자막/배속/화질)
  - 인증 흐름 · 댓글/리뷰/별점 · 마이페이지/알림 · 결제/멤버십 관리 UI
  - 검색/필터/정렬 · 주간 편성 · 작품 상세/모달 UX
- **인프라**
  - nginx 리버스 프록시/secure_link, Docker Compose, Docker Hub, GitHub Actions CD
  - 환경변수/비밀키 관리(SOPS+age 암호화 `.env.enc` 단일 소스, CD가 `AGE_KEY`로 복호화), 루프백 바인딩 보안 하드닝

---

## 외부 연동

- **콘텐츠/메타데이터**: TMDB, Jikan
- **결제**: Iamport (카카오/토스/나이스 채널 키)
- **소셜 로그인**: Google, Kakao, Naver

---

## 환경 변수

- 예시 파일: `env.example`
- 주요 항목: DB(PostgreSQL), Redis, Kafka, RabbitMQ, OAuth2(구글/카카오/네이버), TMDB,
  `BASE_URL`/`COOKIE_DOMAIN`, Iamport, `SECURE_LINK_SECRET`

---

## 문서

- [배포 가이드](docs/deployment.md) — Docker 배포, CI/CD, SSL 설정
- [운영 가이드](docs/operations.md) — 보안 설정, 환경 변수, 모니터링
