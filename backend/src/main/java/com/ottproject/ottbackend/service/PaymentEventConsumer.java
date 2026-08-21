package com.ottproject.ottbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.dto.PaymentSucceededEventDto;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.repository.UserRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * PaymentEventConsumer
 *
 * 큰 흐름
 * - payment.succeeded 토픽을 구독하여 결제 성공의 "부수효과"(영수증 메일 등)를 처리한다.
 * - 결제 정합성 경로(동기 확정)와 완전히 분리되어, 메일 서버 장애가 결제 확정에 영향을 주지 않는다.
 * - 카프카 기본 전달 보장은 at-least-once(중복 배달 가능)이므로 eventId 기준 멱등 처리를 한다.
 * - 처리 중 예외가 나면 재던져서 공통 에러 핸들러(재시도 → DLT)가 격리하도록 한다.
 *
 * 멱등 전략
 * - 처리 시작 전 Redis 에 처리 완료 마킹이 있으면 skip.
 * - 부수효과 성공 후 마킹(TTL 7일). 실패 시 마킹하지 않아 재시도가 가능하다(at-least-once).
 *
 * 왜 확인 → 발송 → 마킹 순서인가(의도된 트레이드오프)
 * - 마킹은 Redis, 발송은 메일 서버라 저장소가 달라 두 동작을 원자적으로 묶을 수 없다.
 *   따라서 중복 발송과 미발송 중 하나는 반드시 감수해야 하며, 이 순서는 중복을 허용하는 대신
 *   유실을 없앤 선택이다(at-least-once). 발송 후 마킹 전에 죽으면 재처리되어 메일이 두 번 간다.
 * - setIfAbsent 로 선점하면 중복은 사라지지만 반대쪽이 열린다. 선점 성공 후 발송 전에 죽으면
 *   키가 TTL 7일 동안 남아 그 사이 재처리가 전부 skip 되고, 영수증은 영구히 유실된다.
 * - 영수증 메일은 중복이 미발송보다 낫기 때문에 이쪽을 골랐다.
 * - 만약 부수효과가 포인트·쿠폰 지급처럼 중복도 유실도 허용되지 않는 것이었다면, 순서를 바꾸는 것으로는
 *   해결되지 않는다. 그때는 부수효과 자체를 DB 트랜잭션 안으로 옮겨 멱등키와 함께 커밋했어야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final ObjectMapper objectMapper; // 이벤트 역직렬화
    private final RedisTemplate<String, Object> redisTemplate; // 멱등 마킹 저장소
    private final UserRepository userRepository; // 수신자 조회
    private final MembershipNotificationService notificationService; // 영수증 메일

    private static final Duration DEDUP_TTL = Duration.ofDays(7); // 멱등 키 보관 기간

    @KafkaListener(topics = "payment.succeeded")
    public void onPaymentSucceeded(String message) throws Exception {
        PaymentSucceededEventDto event = objectMapper.readValue(message, PaymentSucceededEventDto.class);
        String dedupKey = "consumed:payment.succeeded:" + event.getEventId();

        // 멱등: 이미 처리된 이벤트면 skip(중복 배달 방어)
        if (Boolean.TRUE.equals(redisTemplate.hasKey(dedupKey))) {
            log.info("중복 이벤트 skip - eventId: {}", event.getEventId());
            return;
        }

        // 부수효과: 영수증 메일 발송 (실패 시 예외 → 재시도/DLT)
        User user = userRepository
                .findById(event.getUserId())
                .orElseThrow(() -> new IllegalStateException("사용자 없음 - userId: " + event.getUserId()));
        notificationService.sendPaymentReceipt(user, event.getPlanCode(), event.getAmount(), event.getPaidAt());

        // 처리 완료 후 멱등 마킹
        redisTemplate.opsForValue().set(dedupKey, "1", DEDUP_TTL);
        log.info("결제 성공 부수효과 처리 완료 - eventId: {}, paymentId: {}", event.getEventId(), event.getPaymentId());
    }
}
