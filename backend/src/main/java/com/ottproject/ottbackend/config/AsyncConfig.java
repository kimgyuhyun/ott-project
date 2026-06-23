package com.ottproject.ottbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 비동기 처리 설정 클래스
 *
 * 큰 흐름
 * - @Async 기반 비동기 메서드 실행을 활성화한다.
 * - 감사 로그(인증 이벤트) 적재처럼 "기록은 하되 사용자 응답을 지연시키면 안 되는" 작업을
 *   별도 스레드에서 처리하기 위해 사용한다.
 *
 * 참고
 * - 별도 TaskExecutor 를 지정하지 않으면 스프링 기본 실행기를 사용한다.
 *   트래픽이 커지면 전용 ThreadPoolTaskExecutor 빈을 추가해 풀 크기를 제어하는 것을 권장한다.
 */
@Configuration
@EnableAsync // @Async 활성화
public class AsyncConfig {
}
