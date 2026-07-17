package com.ottproject.ottbackend.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OriginValidationFilter 단위 테스트
 *
 * 지키려는 규칙(CSRF 오리진 검증)
 * - 안전 메서드(GET 등)는 검사하지 않는다(상태 변경 아님).
 * - 출처가 우리 도메인이면 통과, 다른 도메인이면 403(실제 공격 시그니처).
 * - Origin 이 없으면 Referer 로 폴백해 판정한다.
 * - Origin/Referer 가 둘 다 없으면 통과(브라우저발 아님 = CSRF 불가, 서버간 호출을 안 깬다).
 * - 결제 웹훅 경로는 검사에서 제외(오검지가 결제 확정을 깨면 치명적).
 * - 킬스위치(enabled=false)면 아무 것도 막지 않는다.
 */
class OriginValidationFilterTest {

    private static final Set<String> ALLOWED = Set.of("https://laputa.kozow.com");

    private final OriginValidationFilter filter = new OriginValidationFilter(ALLOWED, true);

    private MockHttpServletResponse run(OriginValidationFilter f, MockHttpServletRequest req)
            throws ServletException, IOException {
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        f.doFilter(req, res, chain);
        // 체인이 진행됐는지: 진행됐다면 MockFilterChain 이 요청을 기록한다.
        req.setAttribute("__chainProceeded", chain.getRequest() != null);
        return res;
    }

    private boolean proceeded(MockHttpServletRequest req) {
        return Boolean.TRUE.equals(req.getAttribute("__chainProceeded"));
    }

    @Test
    @DisplayName("같은 도메인 POST 는 통과")
    void sameOriginPostPasses() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/reviews");
        req.addHeader("Origin", "https://laputa.kozow.com");

        MockHttpServletResponse res = run(filter, req);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(proceeded(req)).isTrue();
    }

    @Test
    @DisplayName("이웃 서브도메인(evil.kozow.com) POST 는 403 - 공용 도메인 공격 시나리오")
    void crossOriginPostBlocked() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/reviews");
        req.addHeader("Origin", "https://evil.kozow.com");

        MockHttpServletResponse res = run(filter, req);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(proceeded(req)).isFalse(); // 컨트롤러까지 못 감
    }

    @Test
    @DisplayName("Origin 이 없으면 Referer 로 판정 - 우리 도메인이면 통과")
    void refererFallbackPasses() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("PUT", "/api/reviews/1");
        req.addHeader("Referer", "https://laputa.kozow.com/anime/1");

        MockHttpServletResponse res = run(filter, req);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(proceeded(req)).isTrue();
    }

    @Test
    @DisplayName("Origin 이 없고 Referer 가 남의 도메인이면 403")
    void refererFallbackBlocked() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/api/reviews/1");
        req.addHeader("Referer", "https://evil.kozow.com/attack");

        MockHttpServletResponse res = run(filter, req);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(proceeded(req)).isFalse();
    }

    @Test
    @DisplayName("Origin/Referer 둘 다 없으면 통과 - 서버간 호출(SSR/웹훅)은 CSRF 대상 아님")
    void noHeadersPasses() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/reviews");

        MockHttpServletResponse res = run(filter, req);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(proceeded(req)).isTrue();
    }

    @Test
    @DisplayName("GET 은 출처가 남의 도메인이어도 통과 - 상태 변경 아님")
    void safeMethodPasses() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/anime");
        req.addHeader("Origin", "https://evil.kozow.com");

        MockHttpServletResponse res = run(filter, req);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(proceeded(req)).isTrue();
    }

    @Test
    @DisplayName("결제 웹훅은 남의 출처여도 통과 - 서버간 호출 제외 경로")
    void paymentWebhookExempt() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/payments/webhook");
        req.setServletPath("/api/payments/webhook");
        req.addHeader("Origin", "https://some-pg.example.com");

        MockHttpServletResponse res = run(filter, req);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(proceeded(req)).isTrue();
    }

    @Test
    @DisplayName("킬스위치 OFF 면 남의 출처 POST 도 통과")
    void killSwitchDisables() throws Exception {
        OriginValidationFilter disabled = new OriginValidationFilter(ALLOWED, false);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/reviews");
        req.addHeader("Origin", "https://evil.kozow.com");

        MockHttpServletResponse res = run(disabled, req);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(proceeded(req)).isTrue();
    }
}
