package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.exception.DuplicateWebhookEventException;
import com.ottproject.ottbackend.exception.GlobalExceptionHandler;
import com.ottproject.ottbackend.service.PaymentCommandService;
import com.ottproject.ottbackend.service.PaymentMethodService;
import com.ottproject.ottbackend.service.PaymentReadService;
import com.ottproject.ottbackend.util.SecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 API 의 예외 → 응답 경계 테스트
 *
 * 지키려는 규칙
 * - 예외를 HTTP 응답으로 바꾸는 일은 @RestControllerAdvice 한 곳에서만 한다.
 *   그래서 컨트롤러가 아니라 "컨트롤러 + 전역 처리기" 조합을 태워서 결과 응답을 본다.
 * - 응답 바디에 원본 예외 메시지나 내부 클래스명을 싣지 않는다.
 *
 * 특히 중요한 케이스
 * - 웹훅 중복 수신은 200 이어야 한다. 500 을 주면 PG 가 실패로 알고 재전송을 반복한다.
 * - 반대로 그 외의 실패는 200 으로 삼키면 안 된다. PG 가 성공으로 알고 재전송하지 않아 조용한 유실이 된다.
 *   이 두 케이스가 갈리는 지점이라 함께 고정한다.
 *
 * 환경: 전역 처리기 매핑만 확인하면 되므로 standaloneSetup 으로 최소 구성만 띄운다
 * (보안 필터가 없어도 판정에 영향이 없고, 인가 규칙은 별도 슬라이스 테스트가 본다).
 */
@ExtendWith(MockitoExtension.class)
class PaymentExceptionBoundaryTest {

    @Mock private PaymentCommandService paymentCommandService;
    @Mock private PaymentReadService paymentReadService;
    @Mock private SecurityUtil securityUtil;
    @Mock private PaymentMethodService paymentMethodService;
    @Mock private Environment environment;

    @InjectMocks private PaymentController controller;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("웹훅 중복 수신은 200 - PG 재전송 루프를 막는다")
    void duplicateWebhookIsAnsweredWith200() throws Exception {
        willThrow(new DuplicateWebhookEventException("evt_1", new DataIntegrityViolationException("dup key")))
                .given(paymentCommandService).processWebhook(any(), anyString());

        mvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imp_uid\":\"imp_1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("중복 외의 웹훅 실패는 200 으로 삼키지 않는다 - PG 재전송으로 복구돼야 한다")
    void otherWebhookFailureIsNotSwallowed() throws Exception {
        willThrow(new DataIntegrityViolationException("membership row conflict"))
                .given(paymentCommandService).processWebhook(any(), anyString());

        mvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imp_uid\":\"imp_1\"}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("게이트웨이 호출 실패 응답에 예외 메시지와 클래스명이 새지 않는다")
    void gatewayFailureDoesNotLeakExceptionDetail() throws Exception {
        // 예외 메시지에 상류 호스트와 자격증명 거절 사유가 섞여 들어온 상황을 흉내낸다.
        given(paymentReadService.getPaymentStatus(any(), any()))
                .willThrow(new IllegalStateException("rest api key rejected by https://api.iamport.kr/users/getToken"));

        mvc.perform(get("/api/payments/1/status"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("rest api key rejected"))))
                .andExpect(content().string(not(containsString("api.iamport.kr"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
}
