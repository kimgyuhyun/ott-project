package com.ottproject.ottbackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * OriginValidationFilter — CSRF 방어(오리진 검증 방식)
 *
 * 세션 쿠키의 SameSite=Lax 위에 한 겹 더 얹는 서버측 방어.
 *
 * 배경(이 방식을 고른 이유):
 * - 이 서비스는 공용 동적 DNS(laputa.kozow.com) 위에 있다. 브라우저는 same-site 를
 *   등록 도메인(kozow.com) 단위로 판단하므로, 남의 evil.kozow.com 도 same-site 로 잡힌다.
 *   그래서 SameSite=Lax 만으론 이웃 서브도메인발 위조 요청에 세션 쿠키가 샐 수 있다.
 * - 상태변경 요청의 출처(Origin, 없으면 Referer)가 우리 도메인인지 서버가 직접 확인하면
 *   그 구멍을 정확히 막는다. 표준 CSRF 토큰보다 무상태·저결합이라 유지보수도 가볍다.
 *
 * 오작동 방지 원칙:
 * - 안전 메서드(GET/HEAD/OPTIONS/TRACE)는 검사하지 않는다(상태 변경이 아님).
 * - Origin/Referer 가 둘 다 없으면 통과시킨다. CSRF 는 "피해자 브라우저가 쿠키를 자동
 *   첨부"해야 성립하는데, 브라우저 문맥이 아니면(SSR 서버간 호출·결제 웹훅 등) 둘 다 없다.
 *   즉 헤더 부재 = 브라우저발 아님 = CSRF 불가 → 막을 이유가 없다(정상 서버 호출을 안 깬다).
 * - 출처가 "있는데 우리 도메인이 아닐 때"만 403. 이게 실제 공격 시그니처다.
 * - 결제 웹훅 경로는 명시적으로도 건너뛴다(오검지가 결제 확정을 깨면 치명적).
 */
public class OriginValidationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OriginValidationFilter.class);

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final Set<String> allowedOrigins;
    private final boolean enabled;

    public OriginValidationFilter(Set<String> allowedOrigins, boolean enabled) {
        this.allowedOrigins = allowedOrigins;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!enabled) return true;
        if (SAFE_METHODS.contains(request.getMethod())) return true;
        // 결제 웹훅(서버→서버)은 브라우저 오리진이 없고, 오검지 시 결제 확정이 깨지므로 제외
        String path = request.getServletPath();
        return path != null
                && (path.equals("/api/payments/webhook")
                    || (path.startsWith("/api/payments/") && path.endsWith("/webhook")));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String source = request.getHeader("Origin");
        if (source == null) {
            source = originOf(request.getHeader("Referer")); // Origin 없으면 Referer 로 폴백
        }

        // 출처가 있는데 우리 도메인이 아니면 차단. 둘 다 없으면(브라우저발 아님) 통과.
        if (source != null && !allowedOrigins.contains(source)) {
            log.warn("CSRF(origin) 차단: method={} path={} origin={}",
                    request.getMethod(), request.getRequestURI(), source);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"Invalid request origin\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Referer(전체 URL)에서 scheme://host[:port] 만 뽑아 Origin 형태로 정규화. 파싱 실패 시 null. */
    private static String originOf(String referer) {
        if (referer == null || referer.isBlank()) return null;
        try {
            URI u = URI.create(referer);
            if (u.getScheme() == null || u.getHost() == null) return null;
            String origin = u.getScheme() + "://" + u.getHost();
            if (u.getPort() != -1) origin = origin + ":" + u.getPort();
            return origin;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
