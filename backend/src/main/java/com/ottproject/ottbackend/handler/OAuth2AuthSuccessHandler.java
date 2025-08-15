package com.ottproject.ottbackend.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * OAuth2 소셜 로그인 실패 시 처리하는 핸들러
 * 소셜 로그인 실패 시 에러 정보를 JSON 형태로 반환
 *
 * 주요 기능:
 * 1. 소셜 로그인 실패 시 에러 로그 기록
 * 2. JSON 형태의 에러 응답 전송
 * 3. 적절한 HTTP 상태 코드 설정
 */
@Slf4j // 로깅 어노테이션 - log 객체 자동 생성
@Component // Spring Bean으로 등록
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성
public class OAuth2AuthSuccessHandler extends SimpleUrlAuthenticationFailureHandler {

    private final ObjectMapper objectMapper; // JSON 변환용 객체 주입

    /**
     * OAuth2 소셜 로그인 실패 시 호출되는 메서드
     *
     * @param request HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @param exception 인증 실패 예외 객체
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        log.error("OAuth2 로그인 실패: {}", exception.getMessage(), exception); // 에러 로그 기록

        // 요청 헤더에서 Accept 타입 확인 (AJAX 요청인지 일반 요청인지 판단)
        String acceptHeader = request.getHeader("Accept"); // Accept 헤더 확인
        boolean isAjaxRequest = acceptHeader != null && acceptHeader.contains("application/json"); // JSON 요청인지 확인

        if (isAjaxRequest) {
            // AJAX 요청인 경우 JSON 응답 전송
            sendErrorResponse(response, exception); // JSON 에러 응답 전송
        } else {
            // 일반 요청인 경우 프론트엔드로 리다이렉션 (SPA 애플리케이션용)
            String redirectUrl = "http://localhost:3000/oauth2/failure?error=" + 
                    java.net.URLEncoder.encode(exception.getMessage(), "UTF-8"); // 에러 메시지를 URL 파라미터로 전달
            getRedirectStrategy().sendRedirect(request, response, redirectUrl); // 리다이렉션 전송
        }
    }

    /**
     * 에러 응답 전송 (JSON 형태)
     * 소셜 로그인 실패 시 에러 정보를 JSON으로 변환하여 전송
     *
     * @param response HTTP 응답 객체
     * @param exception 인증 실패 예외 객체
     */
    private void sendErrorResponse(HttpServletResponse response, AuthenticationException exception) throws IOException { // IO 예외를 던질 수 있음
        // 에러 응답 생성 (JSON 형태)
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false); // 성공 여부
        errorResponse.put("message", "소셜 로그인에 실패했습니다."); // 에러 메시지 (마침표 추가)
        errorResponse.put("error", exception.getMessage()); // 상세 에러 메시지
        errorResponse.put("timestamp", System.currentTimeMillis()); // 에러 발생 시간

        // HTTP 상태 코드 설정 (401 Unauthorized)
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8"); // JSON 응답 타입 설정

        // JSON 응답 전송
        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
    }
}