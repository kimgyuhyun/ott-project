package com.ottproject.ottbackend.config;

import com.ottproject.ottbackend.handler.OAuth2AuthSuccessHandler;
import com.ottproject.ottbackend.handler.OAuth2AuthFailureHandler;
import com.ottproject.ottbackend.service.OAuth2UserService;
import com.ottproject.ottbackend.service.LocalUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정 클래스
 * 웹 애플리케이션의 보안 설정을 담당
 *
 * 주요 기능:
 * 1. 비밀번호 암호화 설정 (BCrypt)
 * 2. HTTP 요청 인증/인가 설정
 * 3. OAuth2 소셜 로그인 설정 (Google, Kakao, Naver)
 * 4. 기존 이메일 로그인과 소셜 로그인 통합 관리
 * 5. 세션 관리 설정
 * 6. CSRF, HTTP Basic 인증 설정
 */
@Configuration // spring 설정 클래스
@EnableWebSecurity // spring security 활성화
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성 (의존성 주입용)
public class SecurityConfig {

    private final LocalUserDetailsService localUserDetailsService; // 기존 이메일 로그인용 사용자 서비스 주입
    private final OAuth2UserService OAuth2UserService; // OAuth2 소셜 로그인용 사용자 서비스 주입
    private final OAuth2AuthFailureHandler oAuth2AuthFailureHandler; // OAuth2 성공 핸들러 주입
    private final OAuth2AuthSuccessHandler oAuth2AuthSuccessHandler; // OAuth2 실패 핸들러 주입

    @Bean // PasswordEncoder Bean 등록
    public PasswordEncoder passwordEncoder() { // 비밀번호 암호화를 위한 BCrypt 인코더
        return new BCryptPasswordEncoder();
    }

    /**
     * Spring Security 필터 체인 설정
     * 웹 애플리케이션의 모든 보안 설정을 담당
     *
     * @param http HttpSecurity 객체
     * @return SecurityFilterChain 설정된 보안 필터 체인
     * @throws Exception 설정 중 발생할 수 있는 예외
     */
    @Bean // securityFilterChain Bean 등록
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // CSRF 보호 비활성화 (개발용)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll() // 모든 인증 관련 경로 허용
                        .requestMatchers("/oauth2/**").permitAll() // OAuth2 관련 경로 허용 (소셜 로그인)
                        .requestMatchers("/login/oauth2/code/**").permitAll() // OAuth2 콜백 URL 허용
                        .requestMatchers("/api/oauth2/**").permitAll() // OAuth2 API 엔드포인트 허용
                        .requestMatchers("/oauth2/success").permitAll() // OAuth2 성공 페이지 허용
                        .requestMatchers("/oauth2/failure").permitAll() // OAuth2 실패 페이지 허용
                        .requestMatchers("/api/episodes/*/skips").permitAll() // 스킵 메타 조회 공개
                        .requestMatchers("/api/episodes/*/skips/track").permitAll() // 스킵 사용 로깅 공개
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll() // OpenAPI
                        .requestMatchers("/").permitAll() // 루트 경로 허용 (헬스체크용)
                        .requestMatchers("/health").permitAll() // 헬스체크 경로 허용
                        .anyRequest().authenticated() // 그 외 모든 요청은 인증 필요
                )
                .formLogin(form -> form.disable()) // 기본 로그인 폼 비활성화 (REST API용)
                .httpBasic(basic -> basic.disable()) // HTTP Basic 인증 비활성화

                // OAuth2 소셜 로그인 설정
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2AuthFailureHandler) // OAuth2 로그인 성공 시 처리할 핸들러
                        .failureHandler(oAuth2AuthSuccessHandler) // OAuth2 로그인 실패 시 처리할 핸들러
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(OAuth2UserService) // OAuth2 사용자 정보 처리 서비스 (OAuth2UserService 사용)
                        )
                )
                .sessionManagement(session -> session
                        .maximumSessions(1) // 동시 세션 수 제한(1개)
                        .maxSessionsPreventsLogin(false) // 기본 세션 무효화
                )
                .userDetailsService(localUserDetailsService); // 기존 이메일 로그인용 customUserDetailService 등록

        return http.build(); // 설정된 securityFilterChain 반환
    }
}
