package com.ottproject.ottbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.dto.PaymentSucceededEventDto;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Duration;

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
        User user = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new IllegalStateException("사용자 없음 - userId: " + event.getUserId()));
        notificationService.sendPaymentReceipt(user, event.getPlanCode(), event.getAmount(), event.getPaidAt());

        // 처리 완료 후 멱등 마킹
        redisTemplate.opsForValue().set(dedupKey, "1", DEDUP_TTL);
        log.info("결제 성공 부수효과 처리 완료 - eventId: {}, paymentId: {}", event.getEventId(), event.getPaymentId());
    }
}
