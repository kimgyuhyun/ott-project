package com.ottproject.ottbackend.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.enums.AuthEventType;
import com.ottproject.ottbackend.enums.AuthProvider;
import com.ottproject.ottbackend.service.AuthEventService;
import com.ottproject.ottbackend.util.ClientRequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * OAuth2AuthSuccessHandler
 *
 * 큰 흐름
 * - OAuth2 로그인 성공 시 신규 사용자 여부를 확인하고 프론트엔드로 리다이렉트한다.
 *
 * 메서드 개요
 * - onAuthenticationSuccess: 성공 시 신규 사용자 여부 확인 및 리다이렉트
 * - sendSuccessResponse: 성공 정보를 JSON으로 응답
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final ObjectMapper objectMapper;
    private final AuthEventService authEventService; // 소셜 로그인 성공 감사 로그 기록 주입

    /**
     * OAuth2 소셜 로그인 성공 시 호출되는 메서드
     *
     * @param request HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @param authentication 인증 성공 정보
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        log.info("OAuth2 로그인 성공: {}", authentication.getName());

        // 소셜 로그인 성공 감사 로그 기록(비동기) - 통계 스냅샷의 원천 데이터가 됨
        authEventService.record(AuthEventType.LOGIN_SUCCESS, resolveProvider(authentication), authentication.getName(),
                ClientRequestUtil.clientIp(request), ClientRequestUtil.userAgent(request),
                request.getSession(true).getId(), null);

        // 요청 헤더에서 Accept 타입 확인 (AJAX 요청인지 일반 요청인지 판단)
        String acceptHeader = request.getHeader("Accept");
        boolean isAjaxRequest = acceptHeader != null && acceptHeader.contains("application/json");

        if (isAjaxRequest) {
            // AJAX 요청인 경우 JSON 응답 전송
            sendSuccessResponse(response, authentication);
        } else {
            // 일반 요청인 경우 프론트엔드로 리다이렉션
            // 신규 사용자 여부 확인
            boolean isNewUser = false;
            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
                org.springframework.security.oauth2.core.user.OAuth2User oauth2User = 
                    (org.springframework.security.oauth2.core.user.OAuth2User) authentication.getPrincipal();
                isNewUser = Boolean.TRUE.equals(oauth2User.getAttribute("isNewUser"));

                // 세션에 로그인 식별 및 신규 사용자 플래그를 저장하여 프론트 요청에서 참조 가능하도록 함
                try {
                    jakarta.servlet.http.HttpSession session = request.getSession(true);
                    // SessionAuthenticationFilter 호환: 이메일을 세션에 기록
                    String emailFromAuth = authentication.getName();
                    if (emailFromAuth != null && !emailFromAuth.isBlank()) {
                        session.setAttribute("userEmail", emailFromAuth);
                    }
                    session.setAttribute("isNewUser", isNewUser);
                } catch (Exception ignore) { }
            }
            
            // 1) 환경변수 우선
            String envOrigin = System.getenv("FRONTEND_ORIGIN");

            // 2) 프록시 헤더 기반 오리진 산출 (nginx가 X-Forwarded-* 설정)
            String forwardedProto = request.getHeader("X-Forwarded-Proto");
            String forwardedHost = request.getHeader("X-Forwarded-Host");
            String hostHeader = request.getHeader("Host");

            String calculatedOrigin;
            if (envOrigin != null && !envOrigin.isBlank()) {
                calculatedOrigin = envOrigin;
            } else if (forwardedProto != null && forwardedHost != null) {
                calculatedOrigin = forwardedProto + "://" + forwardedHost;
            } else if (hostHeader != null) {
                calculatedOrigin = request.getScheme() + "://" + hostHeader;
            } else {
                calculatedOrigin = request.getScheme() + "://" + request.getServerName()
                        + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : (":" + request.getServerPort()));
            }

            String redirectUrl = calculatedOrigin + "/oauth2/success?newUser=" + isNewUser;
            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
        }
    }

    /**
     * 성공 응답 전송 (JSON 형태)
     * 소셜 로그인 성공 시 사용자 정보를 JSON으로 변환하여 전송
     *
     * @param response HTTP 응답 객체
     * @param authentication 인증 성공 정보
     */
    private void sendSuccessResponse(HttpServletResponse response, Authentication authentication) throws IOException {
        // 성공 응답 생성 (JSON 형태)
        Map<String, Object> successResponse = new HashMap<>();
        successResponse.put("success", true);
        successResponse.put("message", "소셜 로그인이 성공했습니다.");
        successResponse.put("username", authentication.getName());
        
        // 신규 사용자 여부 확인
        boolean isNewUser = false;
        if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
            org.springframework.security.oauth2.core.user.OAuth2User oauth2User = 
                (org.springframework.security.oauth2.core.user.OAuth2User) authentication.getPrincipal();
            isNewUser = Boolean.TRUE.equals(oauth2User.getAttribute("isNewUser"));
        }
        successResponse.put("isNewUser", isNewUser);

        // HTTP 상태 코드 설정 (200 OK)
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");

        // JSON 응답 전송
        String jsonResponse = objectMapper.writeValueAsString(successResponse);
        response.getWriter().write(jsonResponse);
    }

    /**
     * 인증 객체에서 소셜 제공자(AuthProvider)를 산출한다.
     * - OAuth2AuthenticationToken 의 registrationId("google"/"kakao"/"naver")를 enum 으로 매핑한다.
     * - 매핑 불가 시 null 을 반환한다(감사 로그의 provider 는 nullable).
     *
     * @param authentication 인증 성공 정보
     * @return 매핑된 AuthProvider 또는 null
     */
    private AuthProvider resolveProvider(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken token) {
            String registrationId = token.getAuthorizedClientRegistrationId(); // 소셜 등록 ID
            if (registrationId != null) {
                switch (registrationId.toLowerCase()) {
                    case "google":
                        return AuthProvider.GOOGLE;
                    case "kakao":
                        return AuthProvider.KAKAO;
                    case "naver":
                        return AuthProvider.NAVER;
                    default:
                        return null;
                }
            }
        }
        return null;
    }
}