package com.ottproject.ottbackend.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * OAuth2 소셜 로그인 성공 시 처리하는 핸들러
 * CustomOAuth2UserService에서 처리된 사용자 정보를 받아서 성공 응답을 전송
 *
 * 주요 기능:
 * 1. 소셜 로그인 성공 후 JSON 응답 전송
 * 2. 세션에 사용자 정보 저장 (필요한 경우)
 * 3. 성공 로그 기록
 */
@Slf4j // Lombok 로깅 어노테이션 - log 객체 자동 생성
@Component // Spring Bean으로 등록
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler { // SimpleUrlAuthenticationSuccessHandler 상속

    private final ObjectMapper objectMapper; // JSON 변환용 객체 주입

    /**
     * OAuth2 소셜 로그인 성공 시 호출되는 메서드
     * CustomOAuth2UserService에서 이미 사용자 정보 처리가 완료된 상태
     *
     * @param request HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @param authentication 인증 정보 객체 (CustomOAuth2UserService에서 처리된 OAuth2User 포함)
     */
    @Override // 부모 클래스의 메서드를 오버라이드
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException { // IO 예외와 Servlet 예외를 던질 수 있음
        log.info("OAuth2 로그인 성공 처리 시작"); // 로그 출력 - 요청 시작을 알림

        // OAuth2 인증 토큰으로 캐스팅 (소셜 로그인 정보 포함)
        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication; // Authentication을 OAuth2AuthenticationToken으로 캐스팅
        OAuth2User oAuth2User = authToken.getPrincipal(); // OAuth2 사용자 정보 추출

        // CustomOAuth2UserService에서 추가한 사용자 정보 추출
        Map<String, Object> attributes = oAuth2User.getAttributes(); // OAuth2User의 속성 정보 가져오기
        String userEmail = (String) attributes.get("userEmail"); // 사용자 이메일 추출
        String userName = (String) attributes.get("userName"); // 사용자 이름 추출
        String authProvider = (String) attributes.get("authProvider"); // 인증 제공자 추출

        log.info("OAuth2 로그인 성공 - 사용자: {}, 제공자: {}", userEmail, authProvider); // 로그 출력 - 성공 정보 기록

        // 세션에 사용자 정보 저장 (로그인 상태 유지)
        HttpSession session = request.getSession(); // HTTP 세션 객체 가져오기
        session.setAttribute("userEmail", userEmail); // 세션에 이메일 저장
        session.setAttribute("userName", userName); // 세션에 이름 저장
        session.setAttribute("authProvider", authProvider); // 세션에 인증 제공자 저장

        // 요청 헤더에서 Accept 타입 확인 (AJAX 요청인지 일반 요청인지 판단)
        String acceptHeader = request.getHeader("Accept"); // Accept 헤더 확인
        boolean isAjaxRequest = acceptHeader != null && acceptHeader.contains("application/json"); // JSON 요청인지 확인

        // 홈페이지로 리다이렉트 (로그인된 사용자는 홈페이지로)
        String redirectUrl = "http://localhost:8090/"; // 홈페이지로 리다이렉션
        getRedirectStrategy().sendRedirect(request, response, redirectUrl); // 리다이렉션 전송
    }

    /**
     * 성공 응답 전송 (JSON 형태)
     * CustomOAuth2UserService에서 처리된 사용자 정보를 JSON으로 변환하여 전송
     *
     * @param response HTTP 응답 객체
     * @param attributes OAuth2User의 속성 정보 (사용자 정보 포함)
     */
    private void sendSuccessResponse(HttpServletResponse response, Map<String, Object> attributes) throws IOException { // IO 예외를 던질 수 있음
        response.setContentType("application/json;charset=UTF-8"); // JSON 응답 타입 설정
        response.setStatus(HttpServletResponse.SC_OK); // 200 OK 상태 코드 설정

        // 성공 응답 데이터 생성
        Map<String, Object> successResponse = Map.of( // Map.of를 사용하여 불변 Map 생성
                "success", true, // 성공 상태
                "message", "소셜 로그인이 성공했습니다.", // 성공 메시지
                "user", Map.of( // 사용자 정보
                        "id", attributes.get("userId"), // 사용자 ID
                        "email", attributes.get("userEmail"), // 사용자 이메일
                        "name", attributes.get("userName"), // 사용자 이름
                        "role", attributes.get("userRole"), // 사용자 권한
                        "authProvider", attributes.get("authProvider") // 인증 제공자
                )
        );

        // JSON 문자열로 변환하여 응답 전송
        String jsonResponse = objectMapper.writeValueAsString(successResponse); // Map을 JSON 문자열로 변환
        response.getWriter().write(jsonResponse); // 응답에 JSON 문자열 작성
    }
}