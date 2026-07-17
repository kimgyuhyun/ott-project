package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.config.SecurityConfig;
import com.ottproject.ottbackend.handler.OAuth2AuthFailureHandler;
import com.ottproject.ottbackend.handler.OAuth2AuthSuccessHandler;
import com.ottproject.ottbackend.repository.AuthEventRepository;
import com.ottproject.ottbackend.repository.DailyStatsRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import com.ottproject.ottbackend.security.SessionAuthenticationFilter;
import com.ottproject.ottbackend.service.LocalUserDetailsService;
import com.ottproject.ottbackend.service.OAuth2UserService;
import com.ottproject.ottbackend.service.StatsSnapshotService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 API 인가(Authorization) 규칙 테스트
 *
 * 지키려는 규칙
 * - /api/admin/** 은 ROLE_ADMIN 만 접근할 수 있다
 * - 로그인만 했다고(ROLE_USER) 통과하면 안 된다 — 권한 상승 방지
 * - 비로그인은 접근 불가
 *
 * 회귀 배경(2026-07-16)
 * - 소셜 로그인 사용자가 DB 상 ADMIN 인데도 ROLE_ADMIN 권한을 못 받아 관리자 API 가 전부 403 이었다.
 * - 반대 방향(일반 사용자가 관리자 API 를 뚫는 것)도 같은 자리에서 막혀야 하므로 규칙 자체를 고정한다.
 *
 * 이 테스트는 웹 계층만 로드하는 @WebMvcTest 라 Redis/Kafka/RabbitMQ 없이 동작한다.
 */
// application.yml 의 OAuth2 등록정보는 ${GOOGLE_CLIENT_ID:} 처럼 환경변수 기반이라 테스트에서는 비어 있고,
// OAuth2ClientProperties 가 기동 시 "Client id of registration 'google' must not be empty" 로 컨텍스트를 깨뜨린다.
// 인가 규칙만 검증하면 되므로 더미 자격증명을 넣어 프로퍼티 검증만 통과시킨다(실제 소셜 로그인은 하지 않음).
@WebMvcTest(controllers = AdminStatsController.class)
@Import({SecurityConfig.class, SessionAuthenticationFilter.class, WebSliceTestSupport.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "spring.security.oauth2.client.registration.kakao.client-id=test",
        "spring.security.oauth2.client.registration.kakao.client-secret=test",
        "spring.security.oauth2.client.registration.naver.client-id=test",
        "spring.security.oauth2.client.registration.naver.client-secret=test"
})
class AdminAuthorizationTest {

    @Autowired
    private MockMvc mvc;

    // 컨트롤러 의존성
    @MockBean private DailyStatsRepository dailyStatsRepository;
    @MockBean private AuthEventRepository authEventRepository;
    @MockBean private StatsSnapshotService statsSnapshotService;

    // SecurityConfig / 필터 의존성
    @MockBean private LocalUserDetailsService localUserDetailsService;
    @MockBean private OAuth2UserService oAuth2UserService;
    @MockBean private OAuth2AuthSuccessHandler oAuth2AuthSuccessHandler;
    @MockBean private OAuth2AuthFailureHandler oAuth2AuthFailureHandler;
    @MockBean private UserRepository userRepository; // SessionAuthenticationFilter 가 사용
    @MockBean private ClientRegistrationRepository clientRegistrationRepository; // oauth2Login 구성에 필요

    @Test
    @DisplayName("비로그인은 관리자 통계에 접근할 수 없다")
    @WithAnonymousUser
    void anonymousIsDenied() throws Exception {
        mvc.perform(get("/api/admin/stats/daily"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("일반 사용자(ROLE_USER)는 관리자 통계에 접근할 수 없다 - 권한 상승 방지")
    @WithMockUser(roles = "USER")
    void normalUserIsForbidden() throws Exception {
        mvc.perform(get("/api/admin/stats/daily"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자(ROLE_ADMIN)는 관리자 통계에 접근할 수 있다")
    @WithMockUser(roles = "ADMIN")
    void adminIsAllowed() throws Exception {
        mvc.perform(get("/api/admin/stats/daily"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("일반 사용자는 통계 재집계(POST)도 할 수 없다")
    @WithMockUser(roles = "USER")
    void normalUserCannotRebuildStats() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/admin/stats/daily/rebuild"))
                .andExpect(status().isForbidden());
    }
}
