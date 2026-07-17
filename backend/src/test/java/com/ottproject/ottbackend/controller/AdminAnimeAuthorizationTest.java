package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.config.SecurityConfig;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.dto.admin.AdminAnimeDetailDto;
import com.ottproject.ottbackend.dto.admin.AdminAnimeListItemDto;
import com.ottproject.ottbackend.dto.admin.AnimeBulkCurationPreviewResponse;
import com.ottproject.ottbackend.dto.admin.AnimeBulkCurationRequest;
import com.ottproject.ottbackend.dto.admin.AnimeCurationSearchCondition;
import com.ottproject.ottbackend.dto.admin.AnimeCurationUpdateRequest;
import com.ottproject.ottbackend.handler.OAuth2AuthFailureHandler;
import com.ottproject.ottbackend.handler.OAuth2AuthSuccessHandler;
import com.ottproject.ottbackend.repository.UserRepository;
import com.ottproject.ottbackend.security.SessionAuthenticationFilter;
import com.ottproject.ottbackend.service.AnimeCurationService;
import com.ottproject.ottbackend.service.AnimeEnhancementService;
import com.ottproject.ottbackend.service.LocalUserDetailsService;
import com.ottproject.ottbackend.service.OAuth2UserService;
import com.ottproject.ottbackend.service.SimpleAnimeDataCollectorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 애니 API 인가(Authorization) 규칙 테스트
 *
 * 지키려는 규칙
 * - /api/admin/anime/** 은 ROLE_ADMIN 만 접근할 수 있다
 * - 로그인만 했다고(ROLE_USER) 통과하면 안 된다 — 큐레이션은 카탈로그를 통째로 바꿀 수 있다
 * - 비로그인은 접근 불가
 *
 * 왜 별도로 고정하나
 * - 이 컨트롤러의 보호는 전적으로 SecurityConfig 의 URL 패턴("/api/admin/**" → hasRole("ADMIN"))에 달려 있다.
 *   이 프로젝트에는 @EnableMethodSecurity 가 없어 @PreAuthorize 를 붙여도 조용히 무시되므로,
 *   경로가 잘못 바뀌면 아무 경고 없이 무방비가 된다.
 * - 특히 /api/admin/public/** 은 permitAll 이고 ADMIN 규칙보다 먼저 매칭된다. 누군가 큐레이션 경로를
 *   그 아래로 옮기면 벌크 수정이 전세계에 열린다. 그 사고는 아래 MappedPathIsProtected 가 잡는다
 *   (경로를 하드코딩한 케이스들은 못 잡는다 — 컨트롤러가 옮겨가도 죽은 URL 에 대해 여전히 403/404 가 나서 통과한다).
 * - 동기화/보강 엔드포인트를 이 컨트롤러로 통합했으므로, 통합 과정에서 보호가 빠지지 않았는지도 함께 본다.
 *
 * CSRF: 기본 비활성(SecurityConfig 의 app.security.csrf.enabled 기본값 false)이라
 * PATCH/POST 결과가 CSRF 토큰 유무에 오염되지 않는다.
 */
@WebMvcTest(controllers = AdminAnimeController.class)
@Import({SecurityConfig.class, SessionAuthenticationFilter.class, WebSliceTestSupport.class})
// application.yml 의 OAuth2 등록정보는 환경변수 기반이라 테스트에서는 비어 있고,
// OAuth2ClientProperties 가 기동 시 "Client id of registration 'google' must not be empty" 로 컨텍스트를 깨뜨린다.
// 인가 규칙만 검증하면 되므로 더미 자격증명으로 프로퍼티 검증만 통과시킨다(실제 소셜 로그인은 하지 않음).
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "spring.security.oauth2.client.registration.kakao.client-id=test",
        "spring.security.oauth2.client.registration.kakao.client-secret=test",
        "spring.security.oauth2.client.registration.naver.client-id=test",
        "spring.security.oauth2.client.registration.naver.client-secret=test"
})
class AdminAnimeAuthorizationTest {

    @Autowired
    private MockMvc mvc;

    // 컨트롤러 의존성
    @MockBean private AnimeCurationService animeCurationService;
    @MockBean private SimpleAnimeDataCollectorService collectorService;
    @MockBean private AnimeEnhancementService animeEnhancementService;

    // SecurityConfig / 필터 의존성
    @MockBean private LocalUserDetailsService localUserDetailsService;
    @MockBean private OAuth2UserService oAuth2UserService;
    @MockBean private OAuth2AuthSuccessHandler oAuth2AuthSuccessHandler;
    @MockBean private OAuth2AuthFailureHandler oAuth2AuthFailureHandler;
    @MockBean private UserRepository userRepository; // SessionAuthenticationFilter 가 사용
    @MockBean private ClientRegistrationRepository clientRegistrationRepository; // oauth2Login 구성에 필요

    private static final String BULK_BODY = """
            {"condition":{"year":2026},"isActive":false,"expectedCount":1}
            """;
    private static final String PREVIEW_BODY = """
            {"year":2026}
            """;
    private static final String UPDATE_BODY = """
            {"isPopular":true}
            """;

    /**
     * ADMIN 이 200 을 받는지 보려면 서비스가 정상 응답해야 한다(목이 null 을 주면 직렬화에서 깨진다).
     */
    private void givenServiceReturnsSomething() {
        given(animeCurationService.search(any(), anyInt(), anyInt()))
                .willReturn(new PagedResponse<>(List.<AdminAnimeListItemDto>of(), 0L, 0, 20));
        given(animeCurationService.get(anyLong()))
                .willReturn(AdminAnimeDetailDto.builder().id(1L).build());
        given(animeCurationService.update(anyLong(), any(AnimeCurationUpdateRequest.class)))
                .willReturn(AdminAnimeDetailDto.builder().id(1L).build());
        given(animeCurationService.previewBulkCuration(any(AnimeCurationSearchCondition.class)))
                .willReturn(new AnimeBulkCurationPreviewResponse(1L, List.of()));
        given(animeCurationService.applyBulkCuration(any(AnimeBulkCurationRequest.class)))
                .willReturn(1L);
    }

    @Nested
    @DisplayName("선언된 경로 자체가 보호 대상인가")
    class MappedPathIsProtected {

        /**
         * 위의 다른 케이스들은 경로를 문자열로 박아 두므로, 컨트롤러가 통째로 다른 prefix 로 옮겨가도
         * 죽은 URL 에 대해 403/404 가 나서 그대로 통과한다. 즉 "옮겨졌다"는 사고를 못 잡는다.
         * 여기서는 컨트롤러가 실제로 선언한 경로를 읽어서 그 경로가 익명에게 닫혀 있는지를 본다.
         *
         * 이걸 두는 이유: /api/admin/public/** 이 permitAll 이고 ADMIN 규칙보다 위에 있어서,
         * 이 컨트롤러를 그 아래로 옮기는 한 줄이면 벌크 수정이 아무 경고 없이 전세계에 열린다.
         */
        private String basePath() {
            return AdminAnimeController.class.getAnnotation(
                    org.springframework.web.bind.annotation.RequestMapping.class).value()[0];
        }

        @Test
        @DisplayName("컨트롤러가 선언한 경로는 익명에게 닫혀 있다 - permitAll 밑으로 옮기면 여기서 깨진다")
        @WithAnonymousUser
        void declaredPathDeniesAnonymous() throws Exception {
            givenServiceReturnsSomething(); // 혹시 통과해 핸들러까지 가더라도 200 여부로 판정되게 한다

            mvc.perform(get(basePath() + "/search")).andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("컨트롤러가 선언한 경로에서 일반 사용자는 금지된다")
        @WithMockUser(roles = "USER")
        void declaredPathForbidsNormalUser() throws Exception {
            givenServiceReturnsSomething();

            mvc.perform(get(basePath() + "/search")).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("비로그인")
    class Anonymous {

        @Test
        @DisplayName("큐레이션 검색에 접근할 수 없다")
        @WithAnonymousUser
        void cannotSearch() throws Exception {
            mvc.perform(get("/api/admin/anime/search")).andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("단건 수정을 할 수 없다")
        @WithAnonymousUser
        void cannotUpdate() throws Exception {
            mvc.perform(patch("/api/admin/anime/1")
                            .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("벌크 수정을 할 수 없다")
        @WithAnonymousUser
        void cannotBulkUpdate() throws Exception {
            mvc.perform(patch("/api/admin/anime/bulk")
                            .contentType(MediaType.APPLICATION_JSON).content(BULK_BODY))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("일반 사용자(ROLE_USER) - 권한 상승 방지")
    class NormalUser {

        @Test
        @DisplayName("큐레이션 검색이 금지된다")
        @WithMockUser(roles = "USER")
        void searchIsForbidden() throws Exception {
            mvc.perform(get("/api/admin/anime/search")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("단건 조회가 금지된다")
        @WithMockUser(roles = "USER")
        void getIsForbidden() throws Exception {
            mvc.perform(get("/api/admin/anime/1")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("단건 수정이 금지된다")
        @WithMockUser(roles = "USER")
        void updateIsForbidden() throws Exception {
            mvc.perform(patch("/api/admin/anime/1")
                            .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("벌크 미리보기가 금지된다")
        @WithMockUser(roles = "USER")
        void previewIsForbidden() throws Exception {
            mvc.perform(post("/api/admin/anime/bulk/preview")
                            .contentType(MediaType.APPLICATION_JSON).content(PREVIEW_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("벌크 수정이 금지된다 - 카탈로그 전체를 바꿀 수 있는 경로다")
        @WithMockUser(roles = "USER")
        void bulkUpdateIsForbidden() throws Exception {
            mvc.perform(patch("/api/admin/anime/bulk")
                            .contentType(MediaType.APPLICATION_JSON).content(BULK_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("동기화도 금지된다 - 컨트롤러 통합 후에도 보호가 유지된다")
        @WithMockUser(roles = "USER")
        void syncIsForbidden() throws Exception {
            mvc.perform(post("/api/admin/anime/sync/1")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("보강도 금지된다 - 컨트롤러 통합 후에도 보호가 유지된다")
        @WithMockUser(roles = "USER")
        void enhanceIsForbidden() throws Exception {
            mvc.perform(post("/api/admin/anime/enhance-all")).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("관리자(ROLE_ADMIN)")
    class Admin {

        @Test
        @DisplayName("큐레이션 검색에 접근할 수 있다")
        @WithMockUser(roles = "ADMIN")
        void canSearch() throws Exception {
            givenServiceReturnsSomething();

            mvc.perform(get("/api/admin/anime/search")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("단건 수정을 할 수 있다")
        @WithMockUser(roles = "ADMIN")
        void canUpdate() throws Exception {
            givenServiceReturnsSomething();

            mvc.perform(patch("/api/admin/anime/1")
                            .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("벌크 미리보기를 할 수 있다")
        @WithMockUser(roles = "ADMIN")
        void canPreview() throws Exception {
            givenServiceReturnsSomething();

            mvc.perform(post("/api/admin/anime/bulk/preview")
                            .contentType(MediaType.APPLICATION_JSON).content(PREVIEW_BODY))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("벌크 수정을 할 수 있다")
        @WithMockUser(roles = "ADMIN")
        void canBulkUpdate() throws Exception {
            givenServiceReturnsSomething();

            mvc.perform(patch("/api/admin/anime/bulk")
                            .contentType(MediaType.APPLICATION_JSON).content(BULK_BODY))
                    .andExpect(status().isOk());
        }
    }
}
