package com.ottproject.ottbackend.security;

import com.ottproject.ottbackend.util.ClientRequestUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * MdcLoggingFilter
 *
 * 큰 흐름
 * - 요청마다 추적용 식별자(requestId)와 클라이언트 IP(clientIp)를 MDC 에 심는다.
 * - logback 패턴이 %X{requestId}/%X{clientIp} 로 이 값을 출력하므로,
 *   한 요청에서 발생한 여러 로그를 동일 requestId 로 묶어 추적할 수 있다.
 * - 같은 값을 응답 헤더(X-Request-Id)로도 내려준다. 사용자가 들고 온 값 하나로 서버 로그를
 *   바로 찾을 수 있어야 하고, 그러려면 로그와 응답에 같은 값이 남아야 한다.
 * - 가장 먼저 실행되도록 우선순위를 높게 두고, 요청 종료 시 반드시 MDC 를 정리해
 *   스레드 풀 재사용으로 인한 값 오염을 막는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 다른 필터보다 먼저 실행되어 MDC 를 선점
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID = "requestId"; // MDC 키: 요청 추적 ID
    private static final String CLIENT_IP = "clientIp"; // MDC 키: 클라이언트 IP
    static final String REQUEST_ID_HEADER = "X-Request-Id"; // 응답 헤더 이름

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String requestId = UUID.randomUUID().toString().substring(0, 8); // 짧은 8자 추적 ID
            MDC.put(REQUEST_ID, requestId);
            MDC.put(CLIENT_IP, ClientRequestUtil.clientIp(request)); // 프록시 고려한 실제 클라이언트 IP
            // 체인 진입 전에 심는다. 뒤에서 응답이 커밋되고 나면 헤더를 더 이상 붙일 수 없고,
            // 그러면 정작 추적이 필요한 실패 응답에만 ID 가 빠진다.
            response.setHeader(REQUEST_ID_HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear(); // 스레드 재사용 시 값 누수 방지를 위해 반드시 정리
        }
    }
}
