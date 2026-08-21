package com.ottproject.ottbackend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.service.ImportPaymentGateway;
import com.ottproject.ottbackend.service.SimpleJikanApiService;
import com.ottproject.ottbackend.service.TmdbApiService;
import com.ottproject.ottbackend.service.TurnstileVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 빈이 소비자별로 의도한 것이 주입되는지 검증
 *
 * 왜 이 테스트가 필요한가
 * - @Qualifier 이름이 어긋나도 컨텍스트는 뜬다. restTemplate 이 @Primary 라서 조용히 그쪽이
 *   주입되고, 로그인 경로가 30초 타임아웃을 다시 물려받는다. 뜨는 것만으로는 확인이 안 된다.
 * - 그래서 주입된 인스턴스가 이름으로 꺼낸 빈과 같은 객체인지(동일성)까지 본다.
 *
 * 컨테이너를 쓰지 않아 testFast 에 포함된다.
 */
@SpringJUnitConfig(
        classes = {
            RestTemplateConfig.class,
            TurnstileVerifier.class,
            ImportPaymentGateway.class,
            TmdbApiService.class,
            SimpleJikanApiService.class,
            RestTemplateWiringTest.TestBeans.class
        })
@TestPropertySource(
        properties = {
            "tmdb.api.key=test-key", // 기본값이 없는 @Value 라 없으면 컨텍스트가 안 뜬다
            "tmdb.api.base-url=https://example.invalid"
        })
class RestTemplateWiringTest {

    @Configuration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() { // TmdbApiService 생성자 의존성
            return new ObjectMapper();
        }
    }

    @Autowired
    @Qualifier("restTemplate")
    RestTemplate collectorTemplate;

    @Autowired
    @Qualifier("turnstileRestTemplate")
    RestTemplate turnstileTemplate;

    @Autowired
    @Qualifier("paymentRestTemplate")
    RestTemplate paymentTemplate;

    @Autowired
    TurnstileVerifier turnstileVerifier;

    @Autowired
    ImportPaymentGateway importPaymentGateway;

    @Autowired
    TmdbApiService tmdbApiService;

    @Autowired
    SimpleJikanApiService simpleJikanApiService;

    @Test
    @DisplayName("세 빈은 서로 다른 인스턴스다")
    void 세_빈은_서로_다른_인스턴스다() {
        assertThat(collectorTemplate).isNotSameAs(turnstileTemplate);
        assertThat(collectorTemplate).isNotSameAs(paymentTemplate);
        assertThat(turnstileTemplate).isNotSameAs(paymentTemplate);
    }

    @Test
    @DisplayName("TurnstileVerifier 는 turnstileRestTemplate 을 주입받는다")
    void turnstile_는_전용_빈을_받는다() {
        assertThat(ReflectionTestUtils.getField(turnstileVerifier, "rest")).isSameAs(turnstileTemplate);
    }

    @Test
    @DisplayName("ImportPaymentGateway 는 paymentRestTemplate 을 주입받는다")
    void 결제는_전용_빈을_받는다() {
        assertThat(ReflectionTestUtils.getField(importPaymentGateway, "rest")).isSameAs(paymentTemplate);
    }

    @Test
    @DisplayName("수집 서비스 둘은 기존 restTemplate 을 주입받는다")
    void 수집은_기존_빈을_받는다() {
        assertThat(ReflectionTestUtils.getField(tmdbApiService, "restTemplate")).isSameAs(collectorTemplate);
        assertThat(ReflectionTestUtils.getField(simpleJikanApiService, "restTemplate"))
                .isSameAs(collectorTemplate);
    }
}
