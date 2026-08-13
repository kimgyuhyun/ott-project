package com.ottproject.ottbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 설정 클래스
 *
 * 큰 흐름
 * - 외부 API 호출용 RestTemplate Bean 을 용도별로 나눈다.
 * - 하나를 공유하면 요청 경로가 수집 배치의 타임아웃을 그대로 물려받아 톰캣 스레드가 오래 묶인다.
 *
 * 빈 개요
 * - restTemplate: 애니 수집(TMDB/Jikan). 운영자가 직접 누르는 관리자 엔드포인트라 오래 걸려도 된다.
 * - turnstileRestTemplate: 로그인/인증코드 발송 경로의 사람 확인.
 * - paymentRestTemplate: 아임포트 결제 API 전체.
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 수집용(TMDB/Jikan) — 기존 값 유지
     *
     * AdminAnimeController 의 관리자 엔드포인트에서 동기로 돈다. 톰캣 스레드를 쓰지만 사용자가
     * 기다리는 경로가 아니고(15일간 호출 2건), Jikan 은 레이트리밋 대기와 3회 재시도가 정상 동작이라
     * 짧게 잡으면 수집이 실패한다. 기존 빈 이름을 유지해 한정자 없는 주입이 이 빈으로 간다.
     */
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 연결 타임아웃 10초
        factory.setReadTimeout(30000); // 읽기 타임아웃 30초

        return new RestTemplate(factory);
    }

    /**
     * Turnstile(사람 확인)용 — 요청 경로
     *
     * 실측: challenges.cloudflare.com/siteverify 34~52ms(표본 5). 검증 실패는 fail-closed 로
     * 401/403 이 되고 사용자가 다시 시도하면 되므로 짧게 잡아도 잃는 것이 없다.
     */
    @Bean
    public RestTemplate turnstileRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); // 실측 TCP 연결 12~20ms
        factory.setReadTimeout(3000); // 실측 최대 52ms 대비 약 58배 여유

        return new RestTemplate(factory);
    }

    /**
     * 아임포트 결제용 — 요청 경로/웹훅/MQ 소비자/대사 배치가 공유
     *
     * 실측: api.iamport.kr 55~133ms(squid 접근로그 2건 + 직접 프로브 5건). 거의 모든 호출이
     * getToken 을 먼저 부르는 순차 2회라 30초면 한 번의 결제 확인이 스레드를 최대 60초 잡는다.
     * 타임아웃은 실패가 아니라 모호한 상태를 만들지만(ARCHITECTURE 5), 실측 최대의 75배라
     * 정상 호출이 잘릴 여지가 없어 대사 배치가 더 자주 돌 이유도 없다.
     */
    @Bean
    public RestTemplate paymentRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); // 실측 TCP 연결 11~20ms
        factory.setReadTimeout(10000); // 실측 최대 133ms 대비 약 75배 여유

        return new RestTemplate(factory);
    }
}
