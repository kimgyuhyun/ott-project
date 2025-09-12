package com.ottproject.ottbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * JPA Auditing 설정 클래스
 * 
 * 큰 흐름
 * - JPA Auditing에서 한국 시간대를 사용하도록 설정
 * - @CreatedDate, @LastModifiedDate가 한국 시간으로 저장되도록 보장
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "dateTimeProvider")
public class JpaAuditingConfig {

    /**
     * 한국 시간대 Clock Bean
     * 
     * @return 한국 시간대의 Clock
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    /**
     * 한국 시간대를 사용하는 DateTimeProvider Bean
     * 
     * @return 한국 시간대의 현재 시간을 제공하는 DateTimeProvider
     */
    @Bean
    public DateTimeProvider dateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now(clock()));
    }
}
