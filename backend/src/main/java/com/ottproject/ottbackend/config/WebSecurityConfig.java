package com.ottproject.ottbackend.config;

import com.ottproject.ottbackend.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration // spring 설정 클래스
@EnableWebSecurity // spring security 활성화
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성 (의존성 주입용)
public class WebSecurityConfig {

    private  final CustomUserDetailsService customUserDetailsService; // CustomUserDetailService 주입
    @Bean // PasswordEncoder Bean 등록
    public PasswordEncoder passwordEncoder() { // 비밀번호 암호화를 위한 BCrypt 인코더
        return new BCryptPasswordEncoder();
    }

    @Bean // securityFilterChain Bean 등록
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // CSRF 보호 비활성화 (개발용)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll() // 모든 인증 관련 경로 허용
                        .anyRequest().authenticated() // 그 외 모든 요청은 인증 필요
                )
                .formLogin(form -> form.disable()) // 기본 로그인 폼 비활성화 (REST API용)
                .httpBasic(basic -> basic.disable()) // HTTP Basic 인증 비활성화
                .sessionManagement(session -> session
                        .maximumSessions(1) // 동시 세션 수 제한(1개)
                        .maxSessionsPreventsLogin(false) // 기본 세션 무효화
                )
                .userDetailsService(customUserDetailsService); // customUserDetailService 등록
        
        return http.build(); // securityFilterChain 반환
    }
}

