package com.ottproject.ottbackend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 요청 오류 → 응답 경계 테스트
 *
 * 지키려는 규칙(ARCHITECTURE 14절)
 * - 요청이 잘못된 것(없는 메서드, 지원하지 않는 형식, 읽을 수 없는 본문, 타입이 맞지 않는 값, 없는 경로)은
 *   4xx 로 답한다. 서버 결함을 뜻하는 500 으로 답하지 않는다.
 * - 4xx 여도 응답 바디에 원본 예외 메시지와 클래스명을 싣지 않는다.
 *
 * 회귀 배경
 * - GlobalExceptionHandler 의 Exception 처리기가 스프링이 던지는 요청 오류 예외까지 잡아 전부 500 과
 *   ERROR 로그로 바꿨다. 없앤 POST /api/payment-methods 를 부르면 405 가 아니라 500 이 나갔다.
 *
 * 환경: 처리기 매핑만 보면 되므로 PaymentExceptionBoundaryTest 와 같이 standaloneSetup 으로 띄운다.
 */
@ExtendWith(MockitoExtension.class)
class ClientErrorBoundaryTest {

    @Mock
    private PaymentCommandService paymentCommandService;

    @Mock
    private PaymentReadService paymentReadService;

    @Mock
    private SecurityUtil securityUtil;

    @Mock
    private PaymentMethodService paymentMethodService;

    @Mock
    private Environment environment;

    @InjectMocks
    private PaymentController controller;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("매핑에 없는 메서드는 405 이고 허용 메서드를 Allow 헤더로 알려준다")
    void unsupportedMethodIs405() throws Exception {
        mvc.perform(post("/api/payment-methods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(content().string(not(containsString("HttpRequestMethodNotSupportedException"))));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type 은 415")
    void unsupportedMediaTypeIs415() throws Exception {
        mvc.perform(post("/api/payments/checkout")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("planCode=BASIC"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("읽을 수 없는 JSON 본문은 400 이고 파서 메시지가 새지 않는다")
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/api/payments/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(content().string(not(containsString("JSON parse error"))))
                .andExpect(content().string(not(containsString("HttpMessageNotReadableException"))));
    }

    @Test
    @DisplayName("경로 변수 타입이 맞지 않으면 400")
    void pathVariableTypeMismatchIs400() throws Exception {
        mvc.perform(get("/api/payments/abc/status"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("NumberFormatException"))));
    }

    @Test
    @DisplayName("요청 파라미터 형식이 맞지 않으면 400")
    void requestParamTypeMismatchIs400() throws Exception {
        mvc.perform(get("/api/payments/history").param("start", "not-a-date")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("매핑이 없는 경로는 404")
    void unknownPathIs404() throws Exception {
        mvc.perform(get("/api/no-such-path")).andExpect(status().isNotFound());
    }
}
