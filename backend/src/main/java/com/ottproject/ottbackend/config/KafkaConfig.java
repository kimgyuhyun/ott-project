package com.ottproject.ottbackend.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 설정 클래스
 *
 * 큰 흐름
 * - 결제 성공 이벤트 토픽과 DLT(Dead Letter Topic)를 선언한다(KafkaAdmin 이 자동 생성).
 * - 컨슈머 처리 실패 시 고정 백오프로 재시도한 뒤, 소진되면 DLT 로 이관하는 공통 에러 핸들러를 등록한다.
 *   (스프링 부트가 CommonErrorHandler 빈을 자동 구성 리스너 팩토리에 주입한다.)
 *
 * 참고
 * - 단일 브로커(KRaft)라 복제 계수는 1. 파티션은 3(사용자 키 기반 병렬성/순서 보장 데모용).
 * - DeadLetterPublishingRecoverer 기본 규칙: 원본 토픽명 + ".DLT", 동일 파티션으로 발행.
 */
@Configuration
public class KafkaConfig {

    public static final String TOPIC_PAYMENT_SUCCEEDED = "payment.succeeded"; // 결제 성공 이벤트 토픽
    public static final String TOPIC_PAYMENT_SUCCEEDED_DLT = "payment.succeeded.DLT"; // 실패 이벤트 격리 토픽

    @Bean
    public NewTopic paymentSucceededTopic() {
        return TopicBuilder.name(TOPIC_PAYMENT_SUCCEEDED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentSucceededDltTopic() {
        return TopicBuilder.name(TOPIC_PAYMENT_SUCCEEDED_DLT).partitions(3).replicas(1).build();
    }

    /**
     * 공통 에러 핸들러
     * - 컨슈머에서 예외 발생 시 1초 간격 3회 재시도 → 계속 실패하면 DLT 로 이관한다.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate); // 원본토픽.DLT 로 전송
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L)); // 1초 * 3회 재시도 후 DLT
    }
}
