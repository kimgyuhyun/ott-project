package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.config.SecurityConfig;
import com.ottproject.ottbackend.dto.admin.AdminEpisodeDetailDto;
import com.ottproject.ottbackend.handler.OAuth2AuthFailureHandler;
import com.ottproject.ottbackend.handler.OAuth2AuthSuccessHandler;
import com.ottproject.ottbackend.repository.UserRepository;
import com.ottproject.ottbackend.security.SessionAuthenticationFilter;
import com.ottproject.ottbackend.service.AdminEpisodeService;
import com.ottproject.ottbackend.service.LocalUserDetailsService;
import com.ottproject.ottbackend.service.OAuth2UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 에피소드 등록 API 인가(Authorization) 규칙 테스트
 *
 * 지키려는 규칙
 * - 에피소드 등록은 ROLE_ADMIN 만 할 수 있다 — 등록되면 찜한 사용자 전원에게 알림이 나간다.
 *   일반 사용자가 부를 수 있으면 알림 스팸 발송기가 된다.
 *
 * 보호는 전적으로 SecurityConfig 의 URL 패턴("/api/admin/**" → hasRole("ADMIN"))에 달려 있다.
 * 이 프로젝트에는 @EnableMethodSecurity 가 없어 @PreAuthorize 는 조용히 무시된다.
 */
@WebMvcTest(controllers = AdminEpisodeController.class)
@Import({SecurityConfig.class, SessionAuthenticationFilter.class, WebSliceTestSupport.class})
// application.yml 의 OAuth2 등록정보는 환경변수 기반이라 테스트에서는 비어 있어 컨텍스트가 깨진다(더미로 통과시킨다).
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "spring.security.oauth2.client.registration.kakao.client-id=test",
        "spring.security.oauth2.client.registration.kakao.client-secret=test",
        "spring.security.oauth2.client.registration.naver.client-id=test",
        "spring.security.oauth2.client.registration.naver.client-secret=test"
})
class AdminEpisodeAuthorizationTest {

    @Autowired
    private MockMvc mvc;

    @MockBean private AdminEpisodeService adminEpisodeService;

    // SecurityConfig / 필터 의존성
    @MockBean private LocalUserDetailsService localUserDetailsService;
    @MockBean private OAuth2UserService oAuth2UserService;
    @MockBean private OAuth2AuthSuccessHandler oAuth2AuthSuccessHandler;
    @MockBean private OAuth2AuthFailureHandler oAuth2AuthFailureHandler;
    @MockBean private UserRepository userRepository;
    @MockBean private ClientRegistrationRepository clientRegistrationRepository;

    private static final String CREATE_BODY = """
            {"episodeNumber":1,"title":"1화","thumbnailUrl":"https://img/1.jpg","videoUrl":"https://v/1.m3u8","duration":1440}
            """;

    private void givenServiceReturnsSomething() {
        given(adminEpisodeService.createEpisode(anyLong(), any()))
                .willReturn(AdminEpisodeDetailDto.builder().id(1L).animeId(1L).episodeNumber(1).build());
    }

    @Test
    @DisplayName("비로그인은 에피소드를 등록할 수 없다")
    @WithAnonymousUser
    void anonymousCannotCreate() throws Exception {
        mvc.perform(post("/api/admin/animes/1/episodes")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("일반 사용자는 에피소드를 등록할 수 없다 - 알림이 찜한 사용자 전원에게 나가는 경로다")
    @WithMockUser(roles = "USER")
    void normalUserIsForbidden() throws Exception {
        givenServiceReturnsSomething(); // 혹시 통과해 핸들러까지 가더라도 200 여부로 판정되게 한다

        mvc.perform(post("/api/admin/animes/1/episodes")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자는 에피소드를 등록할 수 있다")
    @WithMockUser(roles = "ADMIN")
    void adminCanCreate() throws Exception {
        givenServiceReturnsSomething();

        mvc.perform(post("/api/admin/animes/1/episodes")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isOk());
    }
}
