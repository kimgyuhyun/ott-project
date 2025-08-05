package com.ottproject.ottbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing // JPA Auditing 활성화 (created_at, updated_at 자동 설정)
public class OttBackendServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OttBackendServiceApplication.class, args);
	}

}
