# 결제 이벤트 파이프라인 (Outbox + Kafka)

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

## 확장 지점

부수효과를 **독립 컨슈머**로 더 추가하려면 `@KafkaListener`에 다른 `groupId`를 지정한다(진짜 팬아웃 — 각 그룹이 전체 스트림을 독립 소비).

```java
@KafkaListener(topics = "payment.succeeded", groupId = "ott-stats-consumers")
public void onPaymentForStats(String message) { ... } // 통계 집계
```
