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
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.ottproject.ottbackend.security.SessionAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

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
    private final SessionAuthenticationFilter sessionAuthenticationFilter; // 세션 인증 필터 주입

    @Bean // PasswordEncoder Bean 등록
    public PasswordEncoder passwordEncoder() { // 비밀번호 암호화를 위한 BCrypt 인코더
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS 설정 Bean
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(org.springframework.util.StringUtils.commaDelimitedListToSet(
                System.getProperty("app.cors.allowed-origins",
                        System.getenv().getOrDefault("APP_CORS_ALLOWED_ORIGINS",
                                "http://localhost,http://localhost:3000,http://127.0.0.1,http://127.0.0.1:3000,https://finch-noted-entirely.ngrok-free.app"))
        ).stream().toList());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
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
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // CORS 설정 적용
                .csrf(csrf -> {
                    boolean csrfEnabled = Boolean.parseBoolean(System.getProperty("app.security.csrf.enabled",
                            System.getenv().getOrDefault("APP_SECURITY_CSRF_ENABLED", "false")));
                    if (csrfEnabled) {
                        // 기본 CSRF 활성화: 필요시 ignoringRequestMatchers로 예외 등록 가능
                    } else {
                        csrf.disable();
                    }
                }) // 환경 변수/시스템 프로퍼티로 전환
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll() // 모든 인증 관련 경로 허용
                        .requestMatchers("/oauth2/**").permitAll() // OAuth2 관련 경로 허용 (소셜 로그인)
                        .requestMatchers("/login/oauth2/authorization/**").permitAll() // OAuth2 인가 요청 허용
                        .requestMatchers("/login/oauth2/code/**").permitAll() // OAuth2 콜백 URL 허용
                        .requestMatchers("/api/oauth2/**").permitAll() // OAuth2 API 엔드포인트 허용
                        .requestMatchers("/oauth2/success").permitAll() // OAuth2 성공 페이지 허용
                        .requestMatchers("/oauth2/failure").permitAll() // OAuth2 실패 페이지 허용
                        .requestMatchers("/api/episodes/*/skips").permitAll() // 스킵 메타 조회 공개
                        .requestMatchers("/api/episodes/*/skips/track").permitAll() // 스킵 사용 로깅 공개
                        .requestMatchers("/api/episodes/*/next").permitAll() // 다음 에피소드 조회 공개
                        .requestMatchers("/api/episodes/*/stream-url").authenticated() // 스트림 URL은 인증 필요
                        .requestMatchers("/api/episodes/*/progress").authenticated() // 진행률은 인증 필요
                        .requestMatchers("/api/episodes/progress").authenticated() // 벌크 진행률은 인증 필요
                        .requestMatchers("/api/episodes/mypage/**").authenticated() // 마이페이지는 인증 필요
                        .requestMatchers("/api/admin/public/**").permitAll() // Admin 공개 컨텐츠
                        .requestMatchers("/api/anime/**").permitAll() // 애니메이션 조회 공개 (인증 없이 접근 가능)
                        .requestMatchers("/api/memberships/plans").permitAll() // 멤버십 플랜 조회 공개 (인증 없이 접근 가능)
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll() // OpenAPI
                        .requestMatchers("/").permitAll() // 루트 경로 허용 (헬스체크용)
                        .requestMatchers("/health").permitAll() // 헬스체크 경로 허용
                        .requestMatchers("/api/search/**").permitAll() // 검색(자동완성/본검색) 익명 허용
                        .requestMatchers("/api/payments/webhook", "/api/payments/*/webhook").permitAll() // 결제 웹훅은 인증 없이 수신
                        .requestMatchers("/api/admin/contents/**").hasRole("ADMIN") // Admin DB 관리 전용
                        .requestMatchers("/api/anime/*/reviews").permitAll() // 리뷰 조회는 누구나 접근 가능
                        .requestMatchers("/api/reviews/*/comments").permitAll() // 댓글 조회는 누구나 접근 가능
                        .requestMatchers("/api/episodes/*/comments").permitAll() // 에피소드 댓글 조회는 누구나 접근 가능
                        .anyRequest().authenticated() // 그 외 모든 요청은 인증 필요
                )
                .formLogin(form -> form.disable()) // 기본 로그인 폼 비활성화 (REST API용)
                .httpBasic(basic -> basic.disable()) // HTTP Basic 인증 비활성화

                // REST API: 인증 필요 시 HTML 리다이렉트 대신 401 JSON 반환
                .requestCache(cache -> cache.disable())
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
                }))

                // OAuth2 소셜 로그인 설정
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(a -> a.baseUri("/login/oauth2/authorization")) // 인가 엔드포인트 baseUri 일치
                        .successHandler(oAuth2AuthSuccessHandler) // OAuth2 로그인 성공 시 처리할 핸들러
                        .failureHandler(oAuth2AuthFailureHandler) // OAuth2 로그인 실패 시 처리할 핸들러
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(OAuth2UserService) // OAuth2 사용자 정보 처리 서비스
                        )
                )
                .sessionManagement(session -> session
                        .maximumSessions(1) // 동시 세션 수 제한(1개)
                        .maxSessionsPreventsLogin(false) // 기본 세션 무효화
                )
                .userDetailsService(localUserDetailsService) // 기존 이메일 로그인용 customUserDetailService 등록
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build(); // 설정된 securityFilterChain 반환
    }
}
