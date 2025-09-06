package com.ottproject.ottbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 설정 클래스
 * 
 * 큰 흐름
 * - Jikan API 호출을 위한 RestTemplate Bean을 설정한다.
 * - 타임아웃과 연결 설정을 구성한다.
 * 
 * 설정 개요
 * - connectTimeout: 연결 타임아웃 (10초)
 * - readTimeout: 읽기 타임아웃 (30초)
 */
@Configuration
public class RestTemplateConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 연결 타임아웃 10초 (개발 환경)
        factory.setReadTimeout(30000); // 읽기 타임아웃 30초 (개발 환경)
        
        return new RestTemplate(factory);
    }
}
