package com.ottproject.ottbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 설정
 * - CORS 설정(로컬 Next.js와 연동)
 */
@Configuration
@EnableScheduling
public class MvcConfig implements WebMvcConfigurer {
	@Override
	public void addCorsMappings(@NonNull CorsRegistry registry) { // CORS 매핑 추가
		registry.addMapping("/api/**") // API 경로 허용
				.allowCredentials(true) // 쿠키 전송 허용
				.allowedMethods("GET","POST","PUT","DELETE","OPTIONS") // 메서드 허용
				.allowedHeaders("*") // 헤더 허용
				.allowedOrigins("http://localhost:3000", "http://127.0.0.1:3000"); // 프론트 오리진
	}
}


