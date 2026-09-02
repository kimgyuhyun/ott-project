# OTT Project — 애니메이션 OTT 서비스

사용자 취향을 분석해 콘텐츠를 추천하고, 리뷰·댓글·별점 등 소셜 기능과
멤버십/결제, 에피소드 감상 기능을 제공하는 **애니메이션 OTT 서비스**입니다.
개인화 추천, 정기결제/환불, 재생 권한 제어 등 OTT 핵심 흐름을 다룬 개인 프로젝트입니다.

> 🌐 서비스 주소: **https://laputa.kozow.com** (단일 호스트 Docker Compose 배포)

| 측정한 것 | 결과 |
|---|---|
| 안전 운영 한계 | **약 1,840 RPS** (동시 시청자 약 6,600명 환산 — 목표 부하 500명의 13배) |
| 성능 개선 | 병목(DB 커넥션 풀)을 규명해 **포화점 1,200 → 1,840 RPS (1.5배)** — [측정 기록](loadtest/RESULTS.md) |
| 무너지는 방식 | graceful — 한계 초과에도 909,084 요청 중 **에러 0건**, 지연만 상승 |

숫자의 전제·측정 방법·한계는 [부하 테스트 결과](loadtest/RESULTS.md)에 그대로 적어 뒀습니다.

---

## 주요 기능

| 영역 | 기능 |
|------|------|
| **개인화 추천** | 찜/시청/평점 → 태그 가중치 → 상위 태그 기반 추천 (로그인), 비로그인은 인기작 · 24h 트렌드 |
| **고급 플레이어** | HTML5 Video + 서명 스트림 URL, 이어보기 · 다음 화 자동재생 · OP/ED 스킵 · 배속/자막/화질 |
| **작품/에피소드** | 상세 · 주간 편성표, 태그/장르/성우 메타데이터, 좋아요/찜/시청 진행도 |
| **소셜** | 작품 리뷰 + 리뷰 댓글/대댓글, 에피소드 댓글, 좋아요, 별점, 알림 |
| **마이페이지** | 내 리뷰/댓글, 찜/좋아요, 시청 기록 · 정주행 진행, 활동 요약 |
| **인증/인가** | OAuth2(구글/카카오/네이버) + 이메일 인증, 세션/쿠키 기반 접근 제어, 봇 방어(Turnstile) · CSRF 오리진 검증 <sup>[※](#9-csrf-방어를-토큰이-아니라-오리진-검증으로)</sup> |
| **결제/멤버십** | Iamport 연동 결제/웹훅/환불, 멤버십 기반 재생 권한(4화↑), 구독 해지/재개 · 플랜 변경 |
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
![QueryDSL](https://img.shields.io/badge/QueryDSL-jakarta-blue?style=flat-square)
![Flyway](https://img.shields.io/badge/Flyway-migration-red?style=flat-square&logo=flyway)

- **데이터 접근 3분할** — 일반 쓰기/도메인은 **JPA**, 사용자향 복잡 조회/통계는 **MyBatis**,
  관리자 큐레이션의 동적 검색/벌크 수정은 **QueryDSL**(타입 안전 동적 쿼리)
- **엔티티→DTO 변환은 매핑 라이브러리 없이** — 조회가 MyBatis로 DTO에 직접 채워져 변환 지점 자체가
  적고, 남은 곳은 DTO의 정적 팩토리(`Dto.from(entity)`)로 처리한다
- **Flyway** 스키마 마이그레이션
- **Spring Session (Redis)** — 재배포/다중 인스턴스에서도 세션 유지
- **ShedLock** — `@Scheduled` 배치의 다중 인스턴스 중복 실행 방지(분산락, 저장소는 Redis)
- 테스트: JUnit 5 + **Testcontainers**(운영과 같은 PostgreSQL로 방언 의존 동작 검증)

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

### Infra
![Docker](https://img.shields.io/badge/Docker-Compose-blue?style=flat-square&logo=docker)
![Docker Hub](https://img.shields.io/badge/Docker%20Hub-previous-lightgrey?style=flat-square&logo=docker)
![GHCR](https://img.shields.io/badge/GHCR-private%20registry-181717?style=flat-square&logo=github)
![Nginx](https://img.shields.io/badge/Nginx-reverse%20proxy-green?style=flat-square&logo=nginx)
![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-CD-black?style=flat-square&logo=github-actions)

- **nginx** 리버스 프록시(HTTPS · secure_link), **Docker Compose** 단일 호스트 구성
- **GitHub Actions** → **GHCR**(비공개 레지스트리) 이미지 빌드/푸시 후 서버 배포 — 커밋 SHA 태깅.
  배포 대상은 CI 가 push 직후 캡처한 digest 로 지목하고 CD 는 `@sha256:` 으로만 받는다 —
  [ADR 0010](docs/adr/0010-ci-hands-digest-to-cd.md)
- 레지스트리 이전(**Docker Hub → GHCR**)과 장기 자격증명 대신 `GITHUB_TOKEN` 단기 토큰을 쓰는 건
  **2026-06 크립토재킹 사고**의 재발 방지 조치다 — [사고 회고](docs/incident-2026-06.md)
- **CI 품질 게이트** — main 으로 가는 모든 커밋이 통과해야 하는 검사를 파이프라인에 고정했다.
  시크릿 스캔(히스토리 전량) · 빈 DB 에 Flyway 전량 적용+검증 · 빌드 도구(Gradle wrapper) 무결성 검증 ·
  테스트 *실행 건수* 판정 · SpotBugs 보안 범주 차단 · 이미지 취약점 스캔. 쓰는 액션은 전부 커밋 SHA 로
  고정하고, 워크플로 기본 토큰 권한은 읽기로 두고 필요한 잡에서만 올린다
- 게이트 설계의 핵심은 **차단과 경고를 나눈 것**이다. 스타일·성능 지적까지 차단하면 코드 변경 없이
  파이프라인이 멈추고 결국 게이트를 꺼버리게 되므로, 보안 범주만 차단하고 나머지는 총량을 동결해
  **새로 늘어나는 것만** 막는다. 이미지 스캔도 같은 이유로 **수정 가능한** 취약점만 막는다(패치 없는
  CVE 로 무한 실패하지 않도록). "빌드 성공 = 테스트가 실행됐다" 도 성립하지 않으므로 결과 XML 의
  실행 건수로 따로 판정하고, 리포트가 없거나 분석 대상이 0개면 통과가 아니라 실패로 본다 —
  게이트별 통과 기준은 [배포 가이드](docs/deployment.md)
- 보안 하드닝: 앱/프론트/브로커는 **루프백 전용 바인딩**, 외부 진입점은 nginx(80/443)뿐,
  앱 컨테이너 **루트 파일시스템 읽기 전용**, 백엔드 아웃바운드는 **허용목록 프록시 경유**(아래 [핵심 설계 8번](#8-아웃바운드-통제-목적지-허용목록-프록시))
- **Prometheus + Grafana + Loki** 관측성 (별도 오버레이 — 아래 [성능과 관측성](#성능과-관측성) 참고)

---

## 아키텍처

```
        Browser ──HTTPS──►  80/443  (유일한 공개 포트)
                               ▼
   ╔═══════════════════════════════════════════════════════════╗
   ║  nginx (edge)                          [default · egress]  ║
   ╚══════╤════════════════════════════════════╤═══════════════╝
          │ /*                                 │ /api/* · /login/oauth2/*
          ▼                                    ▼  (upstream 로드밸런싱)
  ┌─────────────────────┐        ┌──────────────────────────────────┐
  │ frontend (:3000)    │        │ app ×2 :  ott-app · ott-app-2     │
  │ Next.js   [default] │        │ Spring Boot (:8090)               │
  │ 인터넷·DB 도달 불가  │        │ [default · data · proxy]          │
  └─────────────────────┘        └───┬──────┬──────┬──────┬──────────┘
                                     ▼      ▼      ▼      ▼   ← data 망 (app만 접근)
                              ┌──────────┐┌───────┐┌──────┐┌──────────┐
                              │PostgreSQL││Redis 7││Kafka ││RabbitMQ  │
                              │  [data]  ││[data] ││[data]││ [data]   │
                              └──────────┘└───────┘└──────┘└──────────┘

  ── 아웃바운드 (백엔드는 인터넷에 직접 닿지 못한다) ───────────────────
     app ×2 ──(proxy 망)──┬──► squid [proxy·egress]   기본 거부 + 목적지 허용목록(10개)
                          │                           → OAuth 3사 · Iamport · Turnstile
                          │                             · TMDB · Jikan
                          └──► sockd [proxy·smtpout]  SOCKS5 (목적지 호스트명 보존)
                                                      → SMTP submission

  ── 관측(monitoring) ───────────────────────────────────────────────
     prometheus ──scrape──► app·app-2        loki ◄──loki4j push── app·app-2
     prometheus · loki ──► grafana (:3001)    [prometheus·loki: default+monitoring]
```

### 계층 구조 (backend)

```
com.ottproject.ottbackend
├── controller   REST 엔드포인트 (@RestController)
├── service      비즈니스 로직 + 결제/추천/정기결제/이벤트 컨슈머
├── entity       JPA 엔티티
├── dto          요청/응답 DTO
├── enums        도메인 enum (결제/구독 상태 등)
├── repository   Spring Data JPA 리포지토리 (+ curation: QueryDSL 동적 쿼리)
├── mybatis      MyBatis 매퍼 (복잡 조회/통계)
├── mappers      매퍼 인터페이스
├── exception    예외 · 전역 핸들러
├── config       Security · Redis · Kafka · Rabbit · QueryDSL · OpenAPI 설정
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
접근 제어를 **URL 발급 게이트(`canStream`) + 엣지 서명 검증(Cloudflare Worker)** 2단으로 구성했습니다.

- `canStream` — 미로그인 차단, 비활성/미공개 차단, **1~3화 무료 · 4화↑ 멤버십 필요**를 실시간 멤버십 상태로 판정
- 영상은 **Cloudflare R2에 올린 실제 다화질 HLS**(마스터 + 3개 렌디션의 `.ts` 세그먼트)이며, **Cloudflare Worker 엣지를 거쳐** 서빙된다. R2 공개 접근은 꺼서 Worker가 유일한 경로다(직링크는 `Unauthorized`).
- 백엔드가 `master.m3u8`에 `secure_link` 형식 서명(`e`/`st`, TTL 6h)을 부착 → Worker가 서명·만료를 검증한 뒤, 응답 플레이리스트를 되쓰며 하위 재생목록·세그먼트에 **엣지가 캐스케이드 서명**을 이어 붙인다. 세그먼트마다 백엔드 서명이나 쿠키 없이 전 구간이 검증되고, 위조·만료 토큰은 **403**이다.
- TTL이 세션 전체를 덮으므로(캐스케이드가 만료값 공유) 재생 중 토큰 만료로 끊기지 않는다. 백엔드-엣지 서명은 URL-safe base64로 바이트 단위 일치.
- 데모 영상은 Blender Foundation 오픈 무비 **Sintel**(CC BY 3.0)를 쓴다 — [외부 연동](#외부-연동) 참고.

### 5. 구독 라이프사이클
- **말일 해지 예약**(`cancelAtPeriodEnd`)과 즉시 해지를 분기, 해지/재개에 **멱등키**로 중복 방지
- **플랜 변경 예약** — 다음 결제일에 반영, 정기결제 배치가 예약분을 함께 처리. 해지된 구독은 배치 대상에서 빠진다(끝난 구독의 플랜이 바뀌고 안내 메일까지 나가면 안 되므로)
- **회원 탈퇴** — 구독을 막지 않고 **즉시 해지**한다. 해지가 말일 해지 예약이라 만료일까지 상태가 `ACTIVE`로 남아, 예전에는 사용자가 탈퇴 조건을 스스로 만족시킬 방법이 없었다. 결제 실패로 던닝 재시도 중인 구독(`PAST_DUE`)도 함께 끊어야 익명화된 계정에 청구가 계속되지 않는다
- 결제 상태 전이(`SUCCEEDED`/`FAILED`/`CANCELED`/`REFUNDED`)와 멤버십/이력 동기화
- **환불 정책** — 24시간 내 · 시청 시간 기준 검증 후 처리

### 6. Redis 캐싱 전략
추천 결과 · 태그 선호도 · 시청 집합 · 24h 트렌드 · 인기 검색어/평균 별점 등 **자주 조회·재계산 비용이 큰 데이터**를 네임스페이스(`ott`) + TTL로 캐싱해 응답 지연과 DB 부하를 낮춥니다.

### 7. 시청 진행률 write-back — 포화점 1.5배
부하 테스트로 **병목을 먼저 규명하고**, 그 지점만 겨냥해 고친 뒤 **같은 조건으로 재측정**했습니다.

- **문제** — 시청 진행률 저장이 전체 요청의 72%(5초마다 1회)인데 매번 DB로 직행했습니다. 포화 구간에서 **Hikari 풀 20/20 고갈 + 대기 스레드 179개**, 지연의 대부분이 쿼리가 아니라 *커넥션을 기다린 시간*이었습니다.
- **해결** — 요청 경로에서는 **Redis 버퍼에만 쓰고**, 스케줄러가 10초마다 버퍼를 통째로 들어내 **배치 upsert** 합니다(`ProgressBufferService`). 다중 인스턴스에서는 ShedLock 분산락으로 flush가 한 번만 돌게 했습니다.
- **결과** — 같은 부하대(약 1,390 RPS)에서 커넥션 **20/20 · 대기 179 → 1~2/20 · 대기 0**, p50 8.4ms → 3.2ms. 포화점 **1,200 → 약 1,840 RPS**, 최대 처리량 1,440 → 약 2,240 RPS. 이전 측정은 5분 46초에 자동 중단됐지만 개선 후에는 11분 시나리오를 완주했습니다.
- **유실 검증** — 부하 종료 후 버퍼 잔량 0, DB 반영 시각이 종료 시각과 일치, flush 오류 0건.
- **병목 이동** — 이제 DB 풀이 아니라 **JVM CPU**입니다(1,390 RPS에서 이미 87%). 다만 목표 부하의 13배까지 나왔으므로 **CPU 개선은 실익이 없다고 판단해 멈췄습니다.**

> ※ *개선 폭 1.5배는 write-back 단독 효과가 아닙니다. 같은 배포에 **OSIV 비활성화**(`open-in-view: false`)가 함께 들어갔고 이것도 커넥션 점유 시간에 직접 영향을 줍니다. 기여도를 나누려면 커밋을 따로 배포해 재야 하는데, 하지 않았습니다.*

### 8. 아웃바운드 통제: 목적지 허용목록 프록시
백엔드는 **인터넷에 직접 닿지 못합니다.** 나가는 모든 요청이 프록시를 거치고, 목록에 없는 목적지는 나가지 못합니다.

- **왜** — 백엔드는 DB가 있는 `data` 망과 인터넷이 열린 `egress` 망에 동시에 있었습니다. 그 컨테이너가 뚫리면 DB를 읽어 **아무 데로나 보낼 수 있습니다.** 망분리는 이걸 못 막습니다. 아웃바운드가 아예 필요 없는 서비스는 끊을 수 있지만, 필요한 서비스의 **목적지**는 못 고르기 때문입니다.
- **구조** — 백엔드를 `egress`에서 빼고 내부망 `proxy`로 옮긴 뒤, 그 망에 프록시 둘을 뒀습니다. HTTP/HTTPS는 **squid**(기본 거부 + 목적지 허용목록 10개), 메일은 **dante sockd**(SOCKS5). 프록시는 `default`·`data`에는 일부러 붙이지 않습니다 — 붙이면 프론트가 프록시를 통해 인터넷을 되찾습니다.
- **메일만 SOCKS인 이유** — SOCKS는 목적지 **호스트명을 그대로 전달**해서 `smtp.naver.com` 인증서 검증(STARTTLS)이 이전과 똑같이 동작합니다. TCP 릴레이였으면 깨집니다. 그리고 JVM 전역 `socksProxyHost`가 아니라 **JavaMailSender 단위**로 겁니다 — 전역 스위치는 `java.net.Socket` 레벨이라 JDBC·Kafka·Redis·RabbitMQ까지 전부 SOCKS로 끌고 갑니다.
- **TLS를 열어보지 않습니다**(`ssl_bump` 없음) — HTTPS는 CONNECT 줄의 평문 호스트명으로 판정하면 충분하고, 가로채기는 경로에 MITM CA를 심는 대가만 남습니다.
- **프록시 이미지는 직접 빌드** — 공개된 squid 이미지에는 전부 패키지 매니저가 들어 있습니다. 인터넷이 열린 유일한 컨테이너에 셸과 패키지 매니저를 두지 않으려고, 설치는 스테이징 루트에서 하고 최종 스테이지는 `FROM scratch`입니다.
- **배포가 매번 검증** — 인스턴스마다 6가지를 확인하고 하나라도 어긋나면 배포가 실패합니다. 허용 목적지가 프록시로 뚫리는가, 비허용 목적지가 **403으로 거부**되는가, 프록시를 우회한 직접 연결이 실패하는가, 외부 DNS가 막히는가, SMTP가 SOCKS로 **220 배너**를 받는가, SOCKS도 비허용 목적지를 거부하는가. 전부 **빈 출력이 아니라 긍정 신호**(종료 코드·403·배너 문자열)로 판정합니다 — 검사가 고장 나서 아무것도 안 나온 것을 "통과"로 읽지 않으려고요.

> ※ *브라우저가 리다이렉트로 가는 주소(OAuth 동의 화면 등)와 이미지 CDN은 허용목록에 없습니다. 백엔드가 그쪽으로 소켓을 열지 않기 때문입니다. `image.tmdb.org`가 실제로 거부되는 것까지 확인했습니다.*

### 9. CSRF 방어를 토큰이 아니라 오리진 검증으로
표준 CSRF 토큰 대신 **서버가 요청 출처를 직접 확인**하는 방식을 택했습니다. 배포 환경 때문입니다.

- 이 서비스는 **공용 동적 DNS**(`laputa.kozow.com`) 위에 있습니다. 브라우저는 same-site를 **등록 도메인**(`kozow.com`) 단위로 판단하므로, 남이 받은 `evil.kozow.com`도 same-site로 잡힙니다. `SameSite=Lax`만으로는 **이웃 서브도메인발 위조 요청**에 세션 쿠키가 샙니다.
- 그래서 상태 변경 요청(GET·HEAD·OPTIONS·TRACE 제외)의 `Origin`, 없으면 `Referer`가 우리 도메인인지 필터에서 확인합니다. 무상태라 토큰 발급·회전·저장이 필요 없습니다.
- 둘 다 없으면 **통과**시킵니다. CSRF는 "피해자 브라우저가 쿠키를 자동으로 붙여 보내는" 공격이고, 브라우저는 상태 변경 요청에 출처 헤더를 붙입니다. 헤더가 없는 건 브라우저가 아닌 클라이언트(서버 간 호출·헬스체크)라 막을 이유가 없습니다.
- 기본 켜짐이되 **킬 스위치**(`APP_SECURITY_ORIGIN_CHECK_ENABLED`)를 둬서, 오탐으로 서비스가 막히면 재배포 없이 끌 수 있게 했습니다.

같은 계층의 다른 방어로, **봇 방어(Cloudflare Turnstile)** 는 로그인 **실패 이후**와 인증코드 발송에만 겁니다. 첫 로그인부터 걸면 정상 사용자까지 매번 통과 절차를 밟게 되므로, 자동화가 드러나는 지점에만 뒀습니다. 시크릿이 없으면 기능이 통째로 no-op이라 로컬 개발이 막히지 않습니다.

---

## 성능과 관측성

![k6](https://img.shields.io/badge/k6-load%20testing-purple?style=flat-square&logo=k6)
![Prometheus](https://img.shields.io/badge/Prometheus-metrics-orange?style=flat-square&logo=prometheus)
![Grafana](https://img.shields.io/badge/Grafana-dashboard-orange?style=flat-square&logo=grafana)
![Loki](https://img.shields.io/badge/Loki-logs-yellow?style=flat-square&logo=grafana)

**"측정 → 병목 규명 → 개선 → 재측정"** 을 한 사이클 돌렸습니다. 그 결과가 [핵심 설계 7번](#7-시청-진행률-write-back--포화점-15배)이고,
아래는 그걸 가능하게 한 도구들입니다.

### 부하 테스트 (k6)

- **closed-loop(`knee`)** — 계단식 VU 증가로 "동시 시청자 몇 명까지"를 봅니다. 1500 VU까지 꺾이지 않았지만, VU가 5초 주기라 **417 RPS가 구조적 상한**이라 한계 자체는 잴 수 없었습니다.
- **open-loop(`stress`)** — `ramping-arrival-rate` 로 목표 RPS를 직접 지정해 **한계와 무너지는 방식**을 봅니다. SLO 이탈 시 자동 중단(`abortOnFail`).
- 측정이 무효가 된 실패도 남겨 뒀습니다 — nginx `limit_req`가 VU 500명을 IP 한 개로 묶어 72%를 막은 건(로그 38,059건과 k6 실패 38,058건 대조로 규명), open-loop에서 로그인을 부하 구간에 두면 *느려짐 → VU 증설 → BCrypt → 더 느려짐* 되먹임이 생긴 건. 인증은 `setup()`으로 빼서 해결했습니다.
- 시나리오·실행법은 [loadtest/README.md](loadtest/README.md), 실측치는 [loadtest/RESULTS.md](loadtest/RESULTS.md).

### DB 쿼리 관측

- `pg_stat_statements` — 쿼리별 누적 호출/시간 집계. `shared_preload_libraries`로 로드하고 확장 생성은 `initdb`로 자동화했습니다.
- `auto_explain` — 임계 시간 초과 쿼리의 실행 계획을 자동 로깅. 임계값(`log_min_duration`)은 재배포 없이 `ALTER SYSTEM`으로 바꿀 수 있게 일부러 커맨드라인에서 뺐습니다(`-c`가 `ALTER SYSTEM`을 이기기 때문).
- **OSIV 비활성화**(`open-in-view: false`) — 뷰 렌더링까지 커넥션을 붙들지 않도록 껐습니다. 커넥션 점유 시간에 직접 영향을 줍니다.

### 메트릭 · 로그

배포한 백엔드가 잘 돌고 있는지 보려고 **Prometheus + Grafana + Loki** 를 붙였습니다.
기존 스택은 그대로 두고 `docker-compose.monitoring.yml` 오버레이로만 얹습니다.

- **메트릭** — Micrometer `/actuator/prometheus` 를 Prometheus 가 수집합니다. 무중단 배포용으로 인스턴스를 둘(`ott-app` · `ott-app-2`) 띄우면 **각 인스턴스를 따로 스크레이프**해서 요청량 · 지연 · JVM 상태를 인스턴스별로 나눠 볼 수 있습니다(롤링 배포 확인용).
- **로그** — logback loki4j appender 로 앱이 직접 Loki 에 로그를 push 합니다(운영 프로파일에서만 전송, 로컬은 no-op). `app` · `instance` 라벨로 어느 인스턴스 로그인지 구분됩니다.
- **대시보드** — 프로비저닝으로 데이터소스 · 대시보드가 자동 등록됩니다. 인스턴스별 UP · 요청량 · 에러율(4xx/5xx) · 평균 응답시간 · 엔드포인트 Top10 · HikariCP · GC · 힙 · CPU · 스레드와 애플리케이션 로그를 한 화면에 뒀습니다.

- **경보** — 기준선 관측 없이 임계값을 정할 수 있는 것만 걸었습니다. 정상값이 **정의상 고정된** 것들입니다. DLQ 유입(정상 0건), 디스크 사용률(상한 100%), 정기결제 배치 정체(주기가 cron 6시간으로 정의됨 → 8시간 넘으면 이상). 오류율·지연 임계는 **일부러 미뤘습니다** — 정상 구간 기준선 없이 숫자를 박으면 오탐이 반복되고, 반복되는 오탐은 결국 경보를 끄게 만듭니다.
- 배치 경보는 **시도가 아니라 완주**를 봅니다. 배치는 사람이 부르지 않는 유일한 매출 경로라 조용히 멈춰도 화면에 아무 변화가 없어서, 완주했을 때만 갱신되는 지표를 두고 그 값이 늙는지로 판정합니다. 다중 인스턴스에서는 ShedLock 때문에 매 주기 하나만 돌므로 인스턴스별이 아니라 **최신값 하나**로 봅니다.
- 컨테이너가 떠 있는지는 호스트의 워치독 스크립트가 5분마다 보고 디스코드로 알립니다(2회 연속 다운일 때만 — 배포 중 재생성을 오탐하지 않으려고). Prometheus로 하지 않은 건 컨테이너 상태를 지표로 만들려면 `docker.sock`을 컨테이너에 넣어야 하는데 그건 거절한 결정이기 때문입니다.

Prometheus · Grafana · Loki 는 `127.0.0.1` 루프백으로만 노출하고(외부 진입점은 nginx 뿐), Grafana 텔레메트리 · 업데이트 확인은 꺼뒀습니다.
경보는 아직 **화면에만 뜹니다** — Alertmanager가 없어 발송 경로는 워치독의 디스코드 웹훅뿐이고, 통합은 남은 과제입니다.

```bash
# 기존 스택 위에 모니터링만 얹기
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  -f docker-compose.netlock.yml -f docker-compose.ha.yml \
  -f docker-compose.monitoring.yml up -d --no-deps prometheus grafana loki
```

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
├── edge/hls-worker/          Cloudflare Worker (HLS 서명 검증 · 캐스케이드 서명)
├── nginx/                    리버스 프록시 설정(HTTPS · secure_link)
├── proxy/                    아웃바운드 프록시 — squid(목적지 허용목록) · dante sockd(SMTP SOCKS5)
├── monitoring/               Prometheus · Grafana · Loki · Alloy 설정
├── security/                 백업 · 감시 · 하드닝 스크립트, DB 최소권한 SQL
├── loadtest/                 k6 시나리오와 측정 기록
├── .github/                  CI/CD 워크플로 · 게이트 스크립트 · Dependabot
├── docs/                     배포 · 운영 · 보안 · 메시징 · 스트리밍 · 회고
├── docker-compose.yml        기본 정의 (+ prod · netlock · ha · monitoring 오버레이)
├── deploy-rolling.ps1        무중단 배포(운영 경로) — 단일 인스턴스는 deploy.ps1
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
  - 재생 권한(`canStream` 게이트 + Cloudflare Worker 엣지 secure_link 서명 검증·캐스케이드)
  - OpenAPI 문서화, Flyway 마이그레이션, MyBatis + JPA 혼용, 통합/서비스 테스트
- **프론트엔드**
  - Next.js App Router, React Query 데이터 패칭/캐싱
  - 플레이어 UI(이어보기 · 다음 화 자동재생 · 스킵/자막/배속/화질)
  - 인증 흐름 · 댓글/리뷰/별점 · 마이페이지/알림 · 결제/멤버십 관리 UI
  - 검색/필터/정렬 · 주간 편성 · 작품 상세/모달 UX
- **인프라**
  - nginx 리버스 프록시, Cloudflare Worker 엣지(HLS secure_link 서명 검증), Docker Compose, GHCR(Docker Hub 에서 이전), GitHub Actions CD
  - CI 품질 게이트(시크릿 스캔 · 마이그레이션 검증 · 테스트 실행 판정 · 정적 분석 · 이미지 취약점 스캔)와 무중단 배포 스크립트
  - 환경변수/비밀키 관리(SOPS+age 암호화 `.env.enc` 단일 소스, CD가 `AGE_KEY`로 복호화), 루프백 바인딩 보안 하드닝
  - 아웃바운드 통제(백엔드를 egress 망에서 분리 → squid 목적지 허용목록 + SMTP SOCKS5 프록시, 배포마다 인스턴스별 6가지 불변식 검증)
  - Prometheus + Grafana + Loki 관측성(인스턴스별 메트릭 · loki4j 로그 push, 별도 오버레이)

---

## 외부 연동

- **콘텐츠/메타데이터**: TMDB, Jikan
- **결제**: Iamport (카카오/토스/나이스 채널 키)
- **소셜 로그인**: Google, Kakao, Naver
- **데모 영상**: [Sintel](https://durian.blender.org/) © Blender Foundation — [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/).
  전 에피소드에 동일 영상을 사용하며, 3화질 ABR HLS로 재인코딩해 Cloudflare R2에서 서빙한다(실제 콘텐츠 대신 재생 파이프라인 시연용).

---

## 환경 변수

- 예시 파일: `env.example`
- 주요 항목: DB(PostgreSQL), Redis, Kafka, RabbitMQ, OAuth2(구글/카카오/네이버), TMDB,
  `BASE_URL`/`COOKIE_DOMAIN`, Iamport, `SECURE_LINK_SECRET`

---

## 문서

- [배포 가이드](docs/deployment.md) — Docker 배포, 무중단 배포, CI/CD 품질 게이트, SSL 설정
- [운영 가이드](docs/operations.md) — 환경 변수, 모니터링, 백업과 복구
- [보안 설계](docs/security.md) — 망분리, 아웃바운드 목적지 허용목록, 이미지 공급망, 인증·최소권한, 애플리케이션 방어
- [비동기 메시징](docs/messaging.md) — 결제 이벤트 파이프라인(Outbox+Kafka), 정기결제 재시도(RabbitMQ TTL+DLX), 다중 인스턴스 중복 발행과 분산락
- [스트리밍](docs/streaming.md) — HLS 서명 재생, 발급 게이트와 엣지 검증, 캐스케이드 서명
- [부하 테스트 결과](loadtest/RESULTS.md) — 포화점 측정, 병목(DB 커넥션 풀) 규명, write-back 개선 후 재측정, 측정 방법의 함정과 남은 한계
- [부하 테스트 실행법](loadtest/README.md) — 시나리오(`slo` · `knee` · `stress`), SLO 임계값, 실행 옵션
- [장애 회고 2026-06](docs/incident-2026-06.md) — 프론트엔드 컨테이너 크립토재킹, 초기 판단의 오류와 재해석, 방어 구조 변경
