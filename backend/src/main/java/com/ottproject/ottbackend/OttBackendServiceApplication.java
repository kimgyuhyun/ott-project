package com.ottproject.ottbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@MapperScan("com.ottproject.ottbackend.mybatis")
@EnableJpaAuditing // JPA Auditing 활성화 (created_at, updated_at 자동 설정)
public class OttBackendServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OttBackendServiceApplication.class, args);
	}

	@PostConstruct
	public void init() {
		// 애플리케이션 시작 시 타임존을 한국 시간으로 설정
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

}
