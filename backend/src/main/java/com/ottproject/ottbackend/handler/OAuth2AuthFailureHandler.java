package com.ottproject.ottbackend.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.enums.AuthEventType;
import com.ottproject.ottbackend.service.AuthEventService;
import com.ottproject.ottbackend.util.ClientRequestUtil;
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
 * OAuth2AuthFailureHandler
 *
 * 큰 흐름
 * - OAuth2 로그인 실패 시 에러를 JSON 응답 또는 리다이렉트로 처리한다.
 *
 * 메서드 개요
 * - onAuthenticationFailure: 실패 시 JSON 응답 또는 실패 전용 URL로 리다이렉트
 * - sendErrorResponse: 실패 정보를 JSON으로 응답
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final ObjectMapper objectMapper;
    private final AuthEventService authEventService; // 소셜 로그인 실패 감사 로그 기록 주입

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
        log.error("OAuth2 로그인 실패: {}", exception.getMessage(), exception);

        // 소셜 로그인 실패 감사 로그 기록(비동기)
        // - 실패 단계에서는 이메일/제공자를 신뢰성 있게 알 수 없어 null 로 두고, 사유만 남긴다.
        authEventService.record(AuthEventType.LOGIN_FAIL, null, null,
                ClientRequestUtil.clientIp(request), ClientRequestUtil.userAgent(request),
                request.getSession(false) != null ? request.getSession(false).getId() : null,
                exception.getMessage());

        // 요청 헤더에서 Accept 타입 확인 (AJAX 요청인지 일반 요청인지 판단)
        String acceptHeader = request.getHeader("Accept");
        boolean isAjaxRequest = acceptHeader != null && acceptHeader.contains("application/json");

        if (isAjaxRequest) {
            // AJAX 요청인 경우 JSON 응답 전송
            sendErrorResponse(response, exception);
        } else {
            // 일반 요청인 경우 프론트엔드로 리다이렉션 (SPA 애플리케이션용)
            String envOrigin = System.getenv("FRONTEND_ORIGIN");
            String forwardedProto = request.getHeader("X-Forwarded-Proto");
            String forwardedHost = request.getHeader("X-Forwarded-Host");
            String hostHeader = request.getHeader("Host");

            String origin;
            if (envOrigin != null && !envOrigin.isBlank()) {
                origin = envOrigin;
            } else if (forwardedProto != null && forwardedHost != null) {
                origin = forwardedProto + "://" + forwardedHost;
            } else if (hostHeader != null) {
                String scheme = request.isSecure() ? "https" : "http";
                origin = scheme + "://" + hostHeader;
            } else {
                String scheme = request.isSecure() ? "https" : "http";
                int port = request.getServerPort();
                origin = scheme + "://" + request.getServerName() + (port == 80 || port == 443 ? "" : ":" + port);
            }

            String redirectUrl = origin + "/oauth2/failure?error=" +
                    java.net.URLEncoder.encode(exception.getMessage(), "UTF-8");
            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
        }
    }

    /**
     * 에러 응답 전송 (JSON 형태)
     * 소셜 로그인 실패 시 에러 정보를 JSON으로 변환하여 전송
     *
     * @param response HTTP 응답 객체
     * @param exception 인증 실패 예외 객체
     */
    private void sendErrorResponse(HttpServletResponse response, AuthenticationException exception) throws IOException {
        // 에러 응답 생성 (JSON 형태)
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", "소셜 로그인에 실패했습니다.");
        errorResponse.put("error", exception.getMessage());
        errorResponse.put("timestamp", System.currentTimeMillis());

        // HTTP 상태 코드 설정 (401 Unauthorized)
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        // JSON 응답 전송
        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
    }
}