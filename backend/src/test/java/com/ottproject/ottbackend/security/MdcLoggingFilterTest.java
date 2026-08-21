package com.ottproject.ottbackend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * MdcLoggingFilter 단위 테스트
 *
 * 지키려는 규칙(상관관계 ID)
 * - 모든 요청에 ID 를 부여하고, 응답 헤더와 로그(MDC)에 "같은 값"을 남긴다.
 *   값이 다르면 사용자가 들고 온 ID 로 로그를 못 찾으므로, 헤더가 붙어 있다는 것만으로는 부족하다.
 * - 요청마다 다른 값이어야 한다(한 요청의 로그만 묶여야 한다).
 * - 처리 중 예외가 나도 헤더는 남아 있어야 한다. 정작 추적이 필요한 건 실패한 요청이다.
 * - 요청이 끝나면 MDC 를 비운다(스레드 재사용 시 다음 요청 로그에 남의 ID 가 붙는 것을 막는다).
 */
class MdcLoggingFilterTest {

    private final MdcLoggingFilter filter = new MdcLoggingFilter();

    @Test
    @DisplayName("응답 헤더의 ID 는 로그에 찍히는 MDC 값과 같은 값이다")
    void responseHeaderCarriesTheSameIdAsTheLogs() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/anime");
        MockHttpServletResponse res = new MockHttpServletResponse();
        String[] seenByLogs = new String[1];
        FilterChain chain = (rq, rs) -> seenByLogs[0] = MDC.get("requestId"); // 로그가 읽는 바로 그 값

        filter.doFilter(req, res, chain);

        assertThat(seenByLogs[0]).isNotBlank();
        assertThat(res.getHeader(MdcLoggingFilter.REQUEST_ID_HEADER)).isEqualTo(seenByLogs[0]);
    }

    @Test
    @DisplayName("요청마다 다른 ID 가 붙는다")
    void eachRequestGetsItsOwnId() throws Exception {
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/anime"), first, new MockFilterChain());
        filter.doFilter(new MockHttpServletRequest("GET", "/api/anime"), second, new MockFilterChain());

        assertThat(first.getHeader(MdcLoggingFilter.REQUEST_ID_HEADER))
                .isNotEqualTo(second.getHeader(MdcLoggingFilter.REQUEST_ID_HEADER));
    }

    @Test
    @DisplayName("처리 중 예외가 나도 응답에 ID 가 남는다 - 실패한 요청이야말로 추적 대상이다")
    void idSurvivesWhenTheHandlerThrows() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/payments/checkout");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain boom = (rq, rs) -> {
            throw new IllegalStateException("handler failed");
        };

        assertThatThrownBy(() -> filter.doFilter(req, res, boom)).isInstanceOf(IllegalStateException.class);

        assertThat(res.getHeader(MdcLoggingFilter.REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    @DisplayName("요청이 끝나면 MDC 를 비운다 - 스레드 재사용 시 남의 ID 가 새지 않는다")
    void mdcIsClearedAfterTheRequest() throws Exception {
        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/anime"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("clientIp")).isNull();
    }
}
