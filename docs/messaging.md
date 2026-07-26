# 비동기 메시징

브로커를 두 개 쓴다. 같은 질문("실패해도 되는 일을 어떻게 본류에서 떼어낼 것인가")에 대한
답이 두 가지라서다.

| | Kafka | RabbitMQ |
|---|---|---|
| 무엇 | 결제 성공 이후 부수효과 팬아웃 | 정기결제 실패 재시도 |
| 필요한 성질 | 순서 있는 스트림, 리플레이, 컨슈머 독립 추가 | 건별 지연 실행, 시간이 되면 정확히 한 번 |
| 핵심 도구 | 아웃박스 테이블 + 파티션 + 오프셋 | TTL + DLX 지연 큐 |

메시지를 여러 소비자에게 흘려보내고 나중에 되감아야 하는 쪽은 Kafka, "3시간 뒤에 이 건만
다시" 처럼 스케줄에 가까운 쪽은 RabbitMQ 가 맞다. 하나로 합치면 한쪽이 반드시 어색해진다.

---

# 1부. 결제 이벤트 파이프라인 (Outbox + Kafka)

## 설계 원칙

**돈이 오가는 확정은 동기, 그 이후 파급효과는 이벤트로 분리한다.**

- 결제 확정(PG 재검증 → `SUCCEEDED` → 멤버십 구독 생성)은 **동기 트랜잭션**으로 정합성을 보장한다. (`PaymentCommandService.markSucceededAndProvision`)
- 결제 성공 "이후" 부수효과(영수증 메일, 통계, 추천 등)는 **Kafka로 비동기 분리**한다. 메일 서버가 죽어도 결제 확정은 영향받지 않는다.

> 참고: 과거 멤버십 구독 생성을 이벤트 리스너로 비동기 처리했다가 "결제는 SUCCEEDED인데 구독은 미생성"인 상태가 발생해, 핵심 프로비저닝은 **동기 직접 호출**로 되돌렸다. 이 경험이 "어디에 async를 쓰고 어디에 consistency를 지킬지" 판단의 근거다.

## 흐름

```
[동기 - 정합성 경로]                         [비동기 - 부수효과 경로]
결제창 성공 콜백 / 웹훅 / 재조정 배치
  └─ markSucceededAndProvision()  (3경로 수렴 + 멱등가드 → 정확히 1회)
        ├─ 결제 SUCCEEDED 확정
        ├─ 멤버십 구독 생성(동기)
        └─ outbox_events INSERT ── 같은 트랜잭션(dual-write 회피)
                                        │
                       OutboxPublisher (2초 폴링) → Kafka: payment.succeeded
                                        │
                          PaymentEventConsumer (@KafkaListener)
                             ├─ eventId 기준 멱등(Redis)
                             └─ 영수증 메일 발송
                                        │ (N회 실패)
                                   payment.succeeded.DLT
```

## 핵심 패턴

| 패턴 | 구현 위치 | 이유 |
|---|---|---|
| **Transactional Outbox** | `OutboxEvent` + `markSucceededAndProvision` 내 INSERT | DB 커밋과 이벤트 발행의 원자성(유실 방지) |
| **폴링 발행기** | `OutboxPublisher` (`@Scheduled`) | 발행 성공 후에만 PUBLISHED 마킹 → at-least-once |
| **멱등 컨슈머** | `PaymentEventConsumer` + Redis `eventId` | Kafka at-least-once의 중복 배달 방어 |
| **DLQ + 재시도** | `KafkaConfig.kafkaErrorHandler` | 1초×3회 재시도 후 `.DLT`로 격리(조용한 유실 방지) |
| **파티션 키** | 발행 시 key = 결제 PK | 동일 애그리거트 이벤트 순서 보장 |

## 운영 커맨드

브로커 컨테이너: `ott-kafka` (공식 apache/kafka, KRaft 단일 노드). CLI는 `/opt/kafka/bin/`에 있고, 컨테이너 안에서 EXTERNAL 리스너(`localhost:9092`)로 접속한다.

```bash
# 토픽 목록
docker exec ott-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# payment.succeeded 이벤트 실시간 확인
docker exec ott-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic payment.succeeded --from-beginning

# DLT(격리된 실패 이벤트) 확인
docker exec ott-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic payment.succeeded.DLT --from-beginning

# 컨슈머 그룹 상태/lag 확인
docker exec ott-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group ott-payment-consumers --describe
```

## 리플레이 (재처리)

정산 로직 버그 등으로 지난 이벤트를 다시 처리해야 할 때, 코드 수정 없이 오프셋을 되감는다.

```bash
# 1) 앱(컨슈머) 중지 후 실행 — 그룹이 활성 상태면 리셋 불가
# 2) 처음부터 재처리
docker exec ott-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group ott-payment-consumers --topic payment.succeeded \
  --reset-offsets --to-earliest --execute

# 특정 시점 이후만 재처리하려면 --to-datetime 사용
#   --reset-offsets --to-datetime 2026-07-01T00:00:00.000 --execute
```

> 멱등 컨슈머 덕분에 이미 처리된 이벤트는 Redis 마킹으로 skip되므로, 리플레이해도 메일이 중복 발송되지 않는다(마킹 TTL 7일 이내 기준).

## 다중 인스턴스와 중복 발행

아웃박스 발행기(`OutboxPublisher`)는 `@Scheduled` 로 돈다. 인스턴스가 1개일 때는 문제가 없지만
2개로 늘리면 **양쪽이 같은 미발행 행을 각자 집어 같은 이벤트를 두 번 발행한다.**

- 재현: `docker-compose.multi.yml` 로 백엔드 2인스턴스를 띄우고 이벤트 50건을 넣으면 100건이 실렸다
- 이 문제는 인스턴스를 늘리기 전까지는 드러나지 않는다. 무중단 배포를 위해 2인스턴스로 가는 순간
  같이 따라오는 비용이다

**해결**: ShedLock(분산락, Redis 프로바이더)으로 스케줄러가 한 번에 한 인스턴스에서만 돌게 한다.

- 설정: `ShedLockConfig` — 기본 `lockAtMostFor` 5분. 락은 Redis 에 `job-lock:ott:<name>` 키로 잡힌다
- 적용: 스케줄러 5개 전부 (아웃박스 발행, 결제 대사, 정기결제, 멤버십 알림, 통계 스냅샷)
- 검증: 같은 방법으로 재측정해 50건 → 50건, 중복 0. 한쪽 인스턴스를 내려도 다른 쪽이 이어받는다
  (ShedLock 은 "항상 같은 인스턴스"가 아니라 "매 실행마다 하나만"을 보장한다)
- `lockAtMostFor` 는 락을 쥔 인스턴스가 죽었을 때 락이 영구히 남지 않도록 하는 상한이고,
  `lockAtLeastFor` 는 실행이 너무 빨리 끝나 다른 인스턴스가 곧바로 이어 도는 것을 막는 하한이다
- **`lockAtMostFor` 는 실제 실행시간보다 넉넉해야 한다.** 초과하면 락이 먼저 풀려 중복 실행된다.
  그래서 정기결제는 `PT30M`(외부 결제 API 를 건건이 호출), 나머지는 `PT9~10M` 으로 잡았다
- 운영 로그 레벨이 INFO 라 ShedLock DEBUG 로그는 보이지 않는다. 동작 확인은 Redis 락 키로 한다
- 컨슈머 쪽 멱등 처리와는 층이 다르다. 멱등 컨슈머는 "중복이 와도 결과가 같게",
  ShedLock 은 "애초에 중복을 만들지 않게" 다룬다. 둘 다 필요하다

## 확장 지점

부수효과를 **독립 컨슈머**로 더 추가하려면 `@KafkaListener`에 다른 `groupId`를 지정한다(진짜 팬아웃 — 각 그룹이 전체 스트림을 독립 소비).

```java
@KafkaListener(topics = "payment.succeeded", groupId = "ott-stats-consumers")
public void onPaymentForStats(String message) { ... } // 통계 집계
```

---

# 2부. 정기결제 재시도 (RabbitMQ TTL + DLX)

## 문제

정기결제는 카드 한도 초과나 일시적 PG 장애로 실패한다. 대부분 시간이 지나면 성공하므로
바로 포기하지 않고 몇 번 더 시도해야 한다(dunning). 다만 **즉시 재시도는 의미가 없다** —
한도 초과는 몇 시간 뒤에나 풀린다.

즉 필요한 건 "3시간 뒤에 이 구독 건만 다시 청구"다. 폴링 배치로 하면 주기마다 전체를 훑어야
하고 지연 시간도 배치 주기만큼 부정확해진다.

## 토폴로지

플러그인 없이 표준 기능만 쓴다. **소비자가 없는 대기 큐에 TTL 을 걸고, 만료된 메시지가
DLX 를 타고 실제 작업 큐로 떨어지는** 구조다.

```
결제 실패 → [billing.retry.wait.first]   TTL 3h   (소비자 없음)
          → [billing.retry.wait.second]  TTL 24h  (소비자 없음)
                    │ TTL 만료
                    ▼
          DLX(billing.retry.exchange, rk=retry)
                    ▼
             [billing.retry.q] → BillingRetryConsumer → 해당 구독만 재청구
```

- 설정: `RabbitConfig`. 지연 값은 `billing.retry.first-delay-ms`, `second-delay-ms`
- 폴링이 아니라 "실패 시점 + 지연"에 정확히 도착하는 건별 재시도다

## 왜 대기 큐를 지연 시간별로 나눴나

RabbitMQ 는 메시지 만료를 **큐 머리에서만** 검사한다. 한 큐에 메시지별 TTL 을 섞어 넣으면,
머리에 있는 긴 TTL 메시지가 뒤의 짧은 TTL 메시지를 막는다(head-of-line blocking).
3시간짜리가 24시간짜리 뒤에 서면 27시간 뒤에 나온다.

지연 값마다 큐를 따로 두면 각 큐 안의 TTL 이 균일해 이 문제가 원천적으로 사라진다.
대신 지연 단계를 늘리려면 큐를 추가해야 한다 — 단계 수가 적을 때 성립하는 트레이드오프다.

## 실패 처리

재시도 처리 중 예외가 나면 **재큐잉하지 않고 폐기한다**(`default-requeue-rejected=false`).
재큐잉하면 같은 메시지가 즉시 되돌아와 무한 루프가 된다.

대신 `nextBillingAt` 기준으로 도는 스윕 배치가 최후 안전망이다. 메시지를 잃어도 다음 주기에
복구된다. **메시지 큐를 유일한 진실로 삼지 않는 것**이 이 설계의 핵심이다.

## 주의

큐 인자(`x-message-ttl` 등)는 **최초 선언 시 고정된다.** 지연 값을 바꾸고 재배포해도 기존
큐에는 반영되지 않고, 인자 불일치로 선언이 실패한다. 값을 바꾸려면 기존 큐를 먼저 삭제해야 한다.
