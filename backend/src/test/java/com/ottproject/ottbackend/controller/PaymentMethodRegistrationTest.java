package com.ottproject.ottbackend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottproject.ottbackend.config.SecurityConfig;
import com.ottproject.ottbackend.handler.OAuth2AuthFailureHandler;
import com.ottproject.ottbackend.handler.OAuth2AuthSuccessHandler;
import com.ottproject.ottbackend.repository.UserRepository;
import com.ottproject.ottbackend.security.SessionAuthenticationFilter;
import com.ottproject.ottbackend.service.LocalUserDetailsService;
import com.ottproject.ottbackend.service.OAuth2UserService;
import com.ottproject.ottbackend.service.PaymentCommandService;
import com.ottproject.ottbackend.service.PaymentMethodService;
import com.ottproject.ottbackend.service.PaymentReadService;
import com.ottproject.ottbackend.util.SecurityUtil;
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
 * 결제수단 등록 경로 부재 테스트
 *
 * 지키려는 규칙(PLATFORM 5절)
 * - 결제사 쪽 청구 수단 식별자(빌링키가 묶인 customer_uid)는 서버가 만들었거나 결제사 조회로 확인한 값만 저장한다.
 *   저장 결제수단은 PaymentCommandService.registerBillingKey 가 결제사에 발급 여부를 확인한 뒤에만 만든다.
 *
 * 회귀 배경
 * - POST /api/payment-methods 가 요청 본문의 providerMethodId 와 isDefault 를 그대로 저장했다.
 *   customer_uid 는 "ott_billing_" + userId 라 추측할 수 있고, 정기결제 배치는 기본 수단부터 그 값으로 청구한다.
 *   그래서 로그인한 사용자가 남의 customer_uid 를 자기 기본 수단으로 등록하면 자기 구독 갱신이 남의 빌링키로 청구됐다.
 * - 정상 화면에서 이 API 를 부르던 곳은 더미 카드 정보를 싣는 카드 등록 모달뿐이었고, providerMethodId 를
 *   보내지 않아 항상 실패했다. 그래서 경로를 고치지 않고 없앴다.
 */
@WebMvcTest(controllers = PaymentController.class)
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
class PaymentMethodRegistrationTest {

    @Autowired
    private MockMvc mvc;

    // 컨트롤러 의존성
    @MockitoBean
    private PaymentCommandService paymentCommandService;

    @MockitoBean
    private PaymentReadService paymentReadService;

    @MockitoBean
    private SecurityUtil securityUtil;

    @MockitoBean
    private PaymentMethodService paymentMethodService;

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
    @DisplayName("로그인한 사용자가 남의 customer_uid 를 기본 결제수단으로 등록하는 요청은 거부되고 아무것도 저장되지 않는다")
    @WithMockUser(roles = "USER")
    void cannotRegisterAnotherUsersBillingKey() throws Exception {
        given(securityUtil.requireCurrentUserId(any())).willReturn(2L); // 요청자는 사용자 2

        mvc.perform(post("/api/payment-methods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"KAKAO_PAY\",\"providerMethodId\":\"ott_billing_1\",\"isDefault\":true}"))
                .andExpect(status().isMethodNotAllowed()); // 목록 조회(GET)만 남아 있다. 등록 API 가 있던 때는 200 이었다

        verifyNoInteractions(paymentMethodService);
    }
}
