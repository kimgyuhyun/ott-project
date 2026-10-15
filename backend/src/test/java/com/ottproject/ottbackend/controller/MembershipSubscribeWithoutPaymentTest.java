package com.ottproject.ottbackend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ottproject.ottbackend.config.SecurityConfig;
import com.ottproject.ottbackend.handler.OAuth2AuthFailureHandler;
import com.ottproject.ottbackend.handler.OAuth2AuthSuccessHandler;
import com.ottproject.ottbackend.repository.UserRepository;
import com.ottproject.ottbackend.security.SessionAuthenticationFilter;
import com.ottproject.ottbackend.service.LocalUserDetailsService;
import com.ottproject.ottbackend.service.MembershipCommandService;
import com.ottproject.ottbackend.service.MembershipReadService;
import com.ottproject.ottbackend.service.OAuth2UserService;
import com.ottproject.ottbackend.util.SecurityUtil;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 결제 없는 구독 생성 경로 부재 테스트 (#195)
 *
 * 지키려는 규칙
 * - 멤버십 구독은 결제가 확정된 뒤에만 생기거나 늘어난다. 그 경로는 PaymentCommandService.markSucceededAndProvision
 *   하나이고, 거기서 MembershipCommandService.subscribe() 를 부른다.
 *
 * 회귀 배경
 * - POST /api/memberships/subscribe 가 요청 본문의 플랜 코드로 subscribe() 를 바로 불렀다. 결제 확인이 없고
 *   인가는 로그인 여부뿐이었다. 로그인한 사용자가 이 요청을 직접 보내면 결제 없이 ACTIVE 구독이 생겼고,
 *   #193 수정 뒤로는 subscribe() 가 활성 구독을 연장하므로 보낼 때마다 한 달씩 늘었다.
 * - 화면에서는 이 API 를 부르지 않는다(프론트의 subscribeMembership/handleSubscribe 는 정의만 있었다).
 *
 * 무엇을 세나
 * - 요청 한 번이 subscribe() 호출 한 번이다. subscribe() 가 실제로 구독 행을 만들거나 기간을 한 달 늘린다는 것은
 *   RepayWhileActiveDoubleBillingTest(실제 DB)와 MembershipCommandServiceTest 가 따로 지킨다.
 * - 기대값은 고치는 방식과 상관없다: 일반 사용자의 이 요청은 4xx 로 끝나고 subscribe() 는 한 번도 불리지 않는다.
 *
 * 출처 헤더는 WebSliceTestSupport 가 허용 Origin 을 기본으로 붙인다(출처 필터 때문에 막혀서 통과하는 일이 없다).
 */
@WebMvcTest(controllers = MembershipController.class)
@Import({SecurityConfig.class, SessionAuthenticationFilter.class, WebSliceTestSupport.class})
@TestPropertySource(
        properties = {
            "spring.security.oauth2.client.registration.google.client-id=test",
            "spring.security.oauth2.client.registration.google.client-secret=test",
            "spring.security.oauth2.client.registration.kakao.client-id=test",
            "spring.security.oauth2.client.registration.kakao.client-secret=test",
            "spring.security.oauth2.client.registration.naver.client-id=test",
            "spring.security.oauth2.client.registration.naver.client-secret=test"
        })
class MembershipSubscribeWithoutPaymentTest {

    @Autowired
    private MockMvc mvc;

    // 컨트롤러 의존성
    @MockitoBean
    private MembershipCommandService membershipCommandService;

    @MockitoBean
    private MembershipReadService membershipReadService;

    @MockitoBean
    private SecurityUtil securityUtil;

    // SecurityConfig / 필터 의존성
    @MockitoBean
    private LocalUserDetailsService localUserDetailsService;

    @MockitoBean
    private OAuth2UserService oAuth2UserService;

    @MockitoBean
    private OAuth2AuthSuccessHandler oAuth2AuthSuccessHandler;

    @MockitoBean
    private OAuth2AuthFailureHandler oAuth2AuthFailureHandler;

    @MockitoBean
    private UserRepository userRepository; // SessionAuthenticationFilter 가 사용

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository; // oauth2Login 구성에 필요

    @Test
    @DisplayName("로그인한 일반 사용자가 결제 없이 구독 신청을 보내도 구독이 생기거나 늘어나지 않는다")
    @WithMockUser(roles = "USER")
    void cannotSubscribeWithoutPayment() throws Exception {
        given(securityUtil.requireCurrentUserId(any())).willReturn(1L);
        int requests = 3; // 한 번이면 새 구독, 이후는 한 달씩 연장된다

        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            statuses.add(mvc.perform(post("/api/memberships/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"planCode\":\"BASIC\"}"))
                    .andReturn()
                    .getResponse()
                    .getStatus());
        }
        long subscribeCalls = mockingDetails(membershipCommandService).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("subscribe"))
                .count();

        System.out.printf(
                "[#195 결제 없는 구독] 요청 %d회 응답=%s, subscribe() 호출=%d회 (결제 0건으로 얻은 이용 기간 %d개월)%n",
                requests, statuses, subscribeCalls, subscribeCalls);

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(statuses).as("응답 상태").allMatch(s -> s >= 400 && s < 500);
        soft.assertThat(subscribeCalls).as("subscribe() 호출 수").isZero();
        soft.assertAll();
    }
}
