package com.ottproject.ottbackend.security;

import com.ottproject.ottbackend.util.ClientRequestUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * MdcLoggingFilter
 *
 * 큰 흐름
 * - 요청마다 추적용 식별자(requestId)와 클라이언트 IP(clientIp)를 MDC 에 심는다.
 * - logback 패턴이 %X{requestId}/%X{clientIp} 로 이 값을 출력하므로,
 *   한 요청에서 발생한 여러 로그를 동일 requestId 로 묶어 추적할 수 있다.
 * - 가장 먼저 실행되도록 우선순위를 높게 두고, 요청 종료 시 반드시 MDC 를 정리해
 *   스레드 풀 재사용으로 인한 값 오염을 막는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 다른 필터보다 먼저 실행되어 MDC 를 선점
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID = "requestId"; // MDC 키: 요청 추적 ID
    private static final String CLIENT_IP = "clientIp";   // MDC 키: 클라이언트 IP

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            MDC.put(REQUEST_ID, UUID.randomUUID().toString().substring(0, 8)); // 짧은 8자 추적 ID
            MDC.put(CLIENT_IP, ClientRequestUtil.clientIp(request)); // 프록시 고려한 실제 클라이언트 IP
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear(); // 스레드 재사용 시 값 누수 방지를 위해 반드시 정리
        }
    }
}
