package com.ottproject.ottbackend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitConfig
 *
 * 큰 흐름
 * - 정기결제 실패 재시도를 위한 지연 큐 토폴로지(TTL+DLX)를 선언한다.
 * - 플러그인 없이 표준 기능만 사용: 소비자 없는 "대기 큐"에 TTL을 걸고,
 *   만료된 메시지가 DLX(Dead Letter Exchange)를 타고 실제 작업 큐로 이동한다.
 *
 * 토폴로지
 *   실패 시 발행 → [billing.retry.wait.first]  (TTL=3h,  소비자 없음)
 *              → [billing.retry.wait.second] (TTL=24h, 소비자 없음)
 *   TTL 만료   → DLX(billing.retry.exchange, rk=retry) → [billing.retry.q] → 리스너가 해당 구독만 재청구
 *
 * 설계 노트
 * - RabbitMQ는 만료를 "큐 머리"에서만 검사하므로(head-of-line blocking),
 *   메시지별 TTL 대신 지연 시간별로 대기 큐를 분리해 순서 문제를 원천 차단한다.
 * - 큐 인자(x-message-ttl 등)는 최초 선언 시 고정된다. 지연 값을 바꾸려면 기존 큐를 삭제해야 한다.
 */
@Configuration
public class RabbitConfig {

	public static final String RETRY_EXCHANGE = "billing.retry.exchange"; // 만료 메시지가 도착하는 DLX
	public static final String RETRY_QUEUE = "billing.retry.q"; // 실제 재시도 작업 큐(리스너 부착)
	public static final String RETRY_ROUTING_KEY = "retry"; // DLX → 작업 큐 라우팅 키
	public static final String WAIT_QUEUE_FIRST = "billing.retry.wait.first"; // 1차 재시도 대기실
	public static final String WAIT_QUEUE_SECOND = "billing.retry.wait.second"; // 2차 재시도 대기실

	@Value("${billing.retry.first-delay-ms:10800000}")
	private long firstDelayMs; // 1차 실패 → 재시도까지 지연(기본 3시간)

	@Value("${billing.retry.second-delay-ms:86400000}")
	private long secondDelayMs; // 2차 실패 → 재시도까지 지연(기본 24시간)

	@Bean
	public DirectExchange billingRetryExchange() { // DLX 겸 작업 큐 진입 교환기
		return new DirectExchange(RETRY_EXCHANGE);
	}

	@Bean
	public Queue billingRetryQueue() { // 재시도 작업 큐(리스너가 소비)
		return QueueBuilder.durable(RETRY_QUEUE).build();
	}

	@Bean
	public Binding billingRetryBinding() { // DLX → 작업 큐 바인딩
		return BindingBuilder.bind(billingRetryQueue()).to(billingRetryExchange()).with(RETRY_ROUTING_KEY);
	}

	@Bean
	public Queue billingRetryWaitFirstQueue() { // 1차 대기실: TTL 만료 시 DLX로 이동
		return QueueBuilder.durable(WAIT_QUEUE_FIRST)
				.withArgument("x-message-ttl", firstDelayMs)
				.withArgument("x-dead-letter-exchange", RETRY_EXCHANGE)
				.withArgument("x-dead-letter-routing-key", RETRY_ROUTING_KEY)
				.build();
	}

	@Bean
	public Queue billingRetryWaitSecondQueue() { // 2차 대기실: TTL 만료 시 DLX로 이동
		return QueueBuilder.durable(WAIT_QUEUE_SECOND)
				.withArgument("x-message-ttl", secondDelayMs)
				.withArgument("x-dead-letter-exchange", RETRY_EXCHANGE)
				.withArgument("x-dead-letter-routing-key", RETRY_ROUTING_KEY)
				.build();
	}

	@Bean
	public Jackson2JsonMessageConverter jacksonMessageConverter() { // 메시지 JSON 직렬화
		return new Jackson2JsonMessageConverter();
	}

	@Bean
	public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) { // JSON 컨버터 적용 템플릿
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		template.setMessageConverter(jacksonMessageConverter());
		return template;
	}
}
