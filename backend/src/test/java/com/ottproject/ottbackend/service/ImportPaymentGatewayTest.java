package com.ottproject.ottbackend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * ImportPaymentGateway 웹훅 검증/결제 재검증 테스트
 *
 * 왜 이 테스트가 필요한가
 * - 아임포트 웹훅에는 서명이 없다. 위조 웹훅을 막는 유일한 관문이 verifyPaymentStatus 의
 *   "아임포트 API 로 금액/주문번호 재조회 후 대조" 다. 이 대조가 무너지면 결제를 만들지 않고도
 *   구독을 받아낼 수 있다. 그런데 이 클래스에는 테스트가 하나도 없었다.
 * - verifyWebhookBasicValidation 은 dev/local 프로파일에서 검증을 통째로 우회한다.
 *   운영에서 우회가 살아나지 않는다는 것을 고정해 둔다.
 *
 * TokenResponse/Token 은 private 중첩 클래스라 테스트에서 직접 만들 수 없다.
 * 프로덕션 가시성을 테스트 때문에 넓히지 않으려고 리플렉션으로 조립한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // 토큰 발급 스텁이 일부 케이스에서 쓰이지 않는다
class ImportPaymentGatewayTest {

    private static final String API_BASE = "https://api.test";
    private static final String IMP_UID = "imp_123";
    private static final String MERCHANT_UID = "order_123";
    private static final long EXPECTED_AMOUNT = 9900L;

    @Mock private RestTemplate rest;

    private ImportPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new ImportPaymentGateway(rest);
        ReflectionTestUtils.setField(gateway, "apiBase", API_BASE);
        ReflectionTestUtils.setField(gateway, "apiKey", "key");
        ReflectionTestUtils.setField(gateway, "apiSecret", "secret");
    }

    /**
     * private 중첩 클래스 ImportPaymentGateway$TokenResponse 를 리플렉션으로 만든다.
     * getAccessToken 이 tr.response.access_token 을 필드로 직접 읽으므로 목으로는 대체할 수 없다.
     */
    private static Object newTokenResponse(String accessToken) throws Exception {
        Class<?> tokenResponseType = Class.forName(
                "com.ottproject.ottbackend.service.ImportPaymentGateway$TokenResponse");
        Class<?> tokenType = Class.forName(
                "com.ottproject.ottbackend.service.ImportPaymentGateway$Token");

        Constructor<?> tokenCtor = tokenType.getDeclaredConstructor();
        tokenCtor.setAccessible(true);
        Object token = tokenCtor.newInstance();
        Field accessTokenField = tokenType.getDeclaredField("access_token");
        accessTokenField.setAccessible(true);
        accessTokenField.set(token, accessToken);

        Constructor<?> responseCtor = tokenResponseType.getDeclaredConstructor();
        responseCtor.setAccessible(true);
        Object tokenResponse = responseCtor.newInstance();
        Field responseField = tokenResponseType.getDeclaredField("response");
        responseField.setAccessible(true);
        responseField.set(tokenResponse, token);

        return tokenResponse;
    }

    /**
     * 아임포트 API 응답을 URL 로 분기해 흉내낸다.
     * - /users/getToken: 토큰 발급
     * - 그 외: paymentBody 를 그대로 반환(null 이면 예외를 던져 조회 실패를 표현)
     */
    private void givenIamportResponds(Map<String, Object> paymentResponse) throws Exception {
        Object tokenResponse = newTokenResponse("test-token");
        given(rest.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                .willAnswer(invocation -> {
                    String url = invocation.getArgument(0);
                    if (url.endsWith("/users/getToken")) {
                        return ResponseEntity.ok(tokenResponse);
                    }
                    return ResponseEntity.ok(Map.of("response", paymentResponse));
                });
    }

    private Map<String, Object> paidPayment(long amount, String merchantUid) {
        return Map.of("status", "paid", "amount", amount, "merchant_uid", merchantUid, "imp_uid", IMP_UID);
    }

    @Nested
    @DisplayName("verifyPaymentStatus - 위조 웹훅을 막는 유일한 관문")
    class VerifyPaymentStatus {

        @Test
        @DisplayName("결제 완료이고 금액과 주문번호가 모두 일치하면 통과시킨다")
        void acceptsMatchingPaidPayment() throws Exception {
            givenIamportResponds(paidPayment(EXPECTED_AMOUNT, MERCHANT_UID));

            assertThat(gateway.verifyPaymentStatus(IMP_UID, MERCHANT_UID, EXPECTED_AMOUNT)).isTrue();
        }

        @Test
        @DisplayName("실제 결제 금액이 기대 금액보다 적으면 거부한다 - 1원 결제로 구독을 받아낼 수 없어야 한다")
        void rejectsUnderpaidAmount() throws Exception {
            givenIamportResponds(paidPayment(1L, MERCHANT_UID));

            assertThat(gateway.verifyPaymentStatus(IMP_UID, MERCHANT_UID, EXPECTED_AMOUNT)).isFalse();
        }

        @Test
        @DisplayName("금액이 더 많아도 기대 금액과 다르면 거부한다 - 정확히 일치해야 한다")
        void rejectsAmountMismatchEvenIfHigher() throws Exception {
            givenIamportResponds(paidPayment(EXPECTED_AMOUNT + 1, MERCHANT_UID));

            assertThat(gateway.verifyPaymentStatus(IMP_UID, MERCHANT_UID, EXPECTED_AMOUNT)).isFalse();
        }

        @Test
        @DisplayName("아직 결제되지 않은 건은 거부한다")
        void rejectsUnpaidStatus() throws Exception {
            givenIamportResponds(Map.of(
                    "status", "ready", "amount", EXPECTED_AMOUNT, "merchant_uid", MERCHANT_UID));

            assertThat(gateway.verifyPaymentStatus(IMP_UID, MERCHANT_UID, EXPECTED_AMOUNT)).isFalse();
        }

        @Test
        @DisplayName("다른 주문의 결제를 가져다 붙이면 거부한다 - 주문번호 대조")
        void rejectsMerchantUidMismatch() throws Exception {
            givenIamportResponds(paidPayment(EXPECTED_AMOUNT, "order_someone_else"));

            assertThat(gateway.verifyPaymentStatus(IMP_UID, MERCHANT_UID, EXPECTED_AMOUNT)).isFalse();
        }

        @Test
        @DisplayName("아임포트에 결제 기록이 없으면 거부한다")
        void rejectsMissingPaymentRecord() throws Exception {
            Object tokenResponse = newTokenResponse("test-token");
            given(rest.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                    .willAnswer(invocation -> {
                        String url = invocation.getArgument(0);
                        if (url.endsWith("/users/getToken")) {
                            return ResponseEntity.ok(tokenResponse);
                        }
                        return ResponseEntity.ok(Map.of()); // response 키 없음
                    });

            assertThat(gateway.verifyPaymentStatus(IMP_UID, MERCHANT_UID, EXPECTED_AMOUNT)).isFalse();
        }
    }

    @Nested
    @DisplayName("verifyPaymentStatus - merchant_uid 폴백(카카오페이 sandbox)")
    class MerchantUidFallback {

        /**
         * imp_uid 단건 조회는 실패시키고, merchant_uid 역조회만 응답하게 한다.
         */
        private void givenSingleLookupFailsAndFindReturns(Map<String, Object> findResponse) throws Exception {
            Object tokenResponse = newTokenResponse("test-token");
            given(rest.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                    .willAnswer(invocation -> {
                        String url = invocation.getArgument(0);
                        if (url.endsWith("/users/getToken")) {
                            return ResponseEntity.ok(tokenResponse);
                        }
                        if (url.contains("/payments/find/")) {
                            return ResponseEntity.ok(Map.of("response", findResponse));
                        }
                        throw new RestClientException("404 존재하지 않는 결제정보");
                    });
        }

        @Test
        @DisplayName("단건 조회가 실패해도 역조회로 금액이 맞으면 통과시킨다")
        void fallsBackToMerchantUidLookup() throws Exception {
            givenSingleLookupFailsAndFindReturns(paidPayment(EXPECTED_AMOUNT, MERCHANT_UID));

            assertThat(gateway.verifyPaymentStatus(IMP_UID, MERCHANT_UID, EXPECTED_AMOUNT)).isTrue();
        }

        @Test
        @DisplayName("폴백 경로에서도 금액이 다르면 거부한다 - 우회로가 되면 안 된다")
        void fallbackStillChecksAmount() throws Exception {
            givenSingleLookupFailsAndFindReturns(paidPayment(1L, MERCHANT_UID));

            assertThat(gateway.verifyPaymentStatus(IMP_UID, MERCHANT_UID, EXPECTED_AMOUNT)).isFalse();
        }

        @Test
        @DisplayName("폴백 경로에서 imp_uid 가 다르면 거부한다 - 남의 결제를 가져다 붙이는 위조 방어")
        void fallbackRejectsImpUidMismatch() throws Exception {
            givenSingleLookupFailsAndFindReturns(
                    Map.of("status", "paid", "amount", EXPECTED_AMOUNT,
                            "merchant_uid", MERCHANT_UID, "imp_uid", "imp_someone_else"));

            assertThat(gateway.verifyPaymentStatus(IMP_UID, MERCHANT_UID, EXPECTED_AMOUNT)).isFalse();
        }

        @Test
        @DisplayName("폴백 경로에서도 결제 완료가 아니면 거부한다")
        void fallbackRejectsUnpaidStatus() throws Exception {
            givenSingleLookupFailsAndFindReturns(
                    Map.of("status", "failed", "amount", EXPECTED_AMOUNT,
                            "merchant_uid", MERCHANT_UID, "imp_uid", IMP_UID));

            assertThat(gateway.verifyPaymentStatus(IMP_UID, MERCHANT_UID, EXPECTED_AMOUNT)).isFalse();
        }
    }

    @Nested
    @DisplayName("chargeWithSavedMethod - 아임포트 논리 실패를 성공으로 오인하면 안 된다")
    class ChargeWithSavedMethod {

        /**
         * /subscribe/payments/again 은 String 으로 응답을 받으므로 원문 JSON 을 그대로 돌려준다.
         */
        private void givenAgainResponds(String rawJson) throws Exception {
            Object tokenResponse = newTokenResponse("test-token");
            given(rest.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                    .willAnswer(invocation -> {
                        String url = invocation.getArgument(0);
                        if (url.endsWith("/users/getToken")) {
                            return ResponseEntity.ok(tokenResponse);
                        }
                        return ResponseEntity.ok(rawJson);
                    });
        }

        private PaymentGateway.ChargeResult charge() {
            return gateway.chargeWithSavedMethod("cust_1", "billing_1", EXPECTED_AMOUNT, "KRW", "Subscription renewal");
        }

        /**
         * 아임포트는 빌링키가 없어도 HTTP 200 을 준다. code 만이 실패를 알려준다.
         */
        @Test
        @DisplayName("code 가 0 이 아니면 ChargeException 을 던진다 - HTTP 200 이어도 실패다")
        void throwsWhenCodeIsNotZero() throws Exception {
            givenAgainResponds("{\"code\":-1,\"message\":\"등록된 빌링키가 없습니다.\",\"response\":null}");

            assertThatThrownBy(this::charge)
                    .isInstanceOf(PaymentGateway.ChargeException.class)
                    .hasMessage("등록된 빌링키가 없습니다.")
                    .extracting(e -> ((PaymentGateway.ChargeException) e).errorCode)
                    .isEqualTo("-1");
        }

        @Test
        @DisplayName("code 가 0 이어도 status 가 paid 가 아니면 ChargeException 을 던진다")
        void throwsWhenStatusIsNotPaid() throws Exception {
            givenAgainResponds("{\"code\":0,\"message\":null,"
                    + "\"response\":{\"status\":\"failed\",\"imp_uid\":\"imp_1\"}}");

            assertThatThrownBy(this::charge).isInstanceOf(PaymentGateway.ChargeException.class);
        }

        @Test
        @DisplayName("code 가 0 이고 status 가 paid 여도 imp_uid 가 없으면 ChargeException 을 던진다")
        void throwsWhenImpUidMissing() throws Exception {
            givenAgainResponds("{\"code\":0,\"message\":null,\"response\":{\"status\":\"paid\"}}");

            assertThatThrownBy(this::charge).isInstanceOf(PaymentGateway.ChargeException.class);
        }

        @Test
        @DisplayName("정상 응답이면 ChargeResult 를 반환한다")
        void returnsResultOnSuccess() throws Exception {
            givenAgainResponds("{\"code\":0,\"message\":null,\"response\":{\"status\":\"paid\","
                    + "\"imp_uid\":\"" + IMP_UID + "\",\"receipt_url\":\"https://receipt.test/1\"}}");

            PaymentGateway.ChargeResult result = charge();

            assertThat(result.providerPaymentId).isEqualTo(IMP_UID);
            assertThat(result.receiptUrl).isEqualTo("https://receipt.test/1");
            assertThat(result.paidAt).isNotNull();
        }
    }

    @Nested
    @DisplayName("verifyWebhookBasicValidation")
    class WebhookBasicValidation {

        private static final String VALID_BODY =
                "{\"imp_uid\":\"imp_1\",\"merchant_uid\":\"order_1\",\"status\":\"paid\"}";

        @Test
        @DisplayName("필수 필드가 모두 있고 상태값이 유효하면 통과시킨다")
        void acceptsWellFormedWebhook() {
            assertThat(gateway.verifyWebhookBasicValidation(VALID_BODY, Map.of())).isTrue();
        }

        @Test
        @DisplayName("본문이 없으면 거부한다")
        void rejectsEmptyBody() {
            assertThat(gateway.verifyWebhookBasicValidation(null, Map.of())).isFalse();
            assertThat(gateway.verifyWebhookBasicValidation("   ", Map.of())).isFalse();
        }

        @Test
        @DisplayName("JSON 이 아니면 거부한다 - 파싱 실패가 예외로 새어나가면 안 된다")
        void rejectsMalformedJson() {
            assertThat(gateway.verifyWebhookBasicValidation("not json at all", Map.of())).isFalse();
        }

        @Test
        @DisplayName("imp_uid 나 merchant_uid 가 빠지면 거부한다")
        void rejectsMissingIdentifiers() {
            assertThat(gateway.verifyWebhookBasicValidation(
                    "{\"merchant_uid\":\"order_1\",\"status\":\"paid\"}", Map.of())).isFalse();
            assertThat(gateway.verifyWebhookBasicValidation(
                    "{\"imp_uid\":\"imp_1\",\"status\":\"paid\"}", Map.of())).isFalse();
        }

        @Test
        @DisplayName("상태값이 비어 있으면 거부한다")
        void rejectsBlankStatus() {
            assertThat(gateway.verifyWebhookBasicValidation(
                    "{\"imp_uid\":\"imp_1\",\"merchant_uid\":\"order_1\",\"status\":\"  \"}", Map.of()))
                    .isFalse();
        }

        @Test
        @DisplayName("아임포트가 쓰지 않는 상태값은 거부한다")
        void rejectsUnknownStatus() {
            assertThat(gateway.verifyWebhookBasicValidation(
                    "{\"imp_uid\":\"imp_1\",\"merchant_uid\":\"order_1\",\"status\":\"succeeded\"}", Map.of()))
                    .isFalse();
        }

        @Test
        @DisplayName("아임포트가 쓰는 네 가지 상태값을 모두 받는다")
        void acceptsAllIamportStatuses() {
            for (String status : new String[]{"ready", "paid", "cancelled", "failed"}) {
                String body = String.format(
                        "{\"imp_uid\":\"imp_1\",\"merchant_uid\":\"order_1\",\"status\":\"%s\"}", status);
                assertThat(gateway.verifyWebhookBasicValidation(body, Map.of()))
                        .as("상태값 %s", status)
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("verifyWebhookBasicValidation - 개발 환경 우회")
    class DevelopmentBypass {

        private String originalProfile;

        @BeforeEach
        void rememberProfile() {
            originalProfile = System.getProperty("spring.profiles.active");
        }

        @AfterEach
        void restoreProfile() {
            if (originalProfile == null) {
                System.clearProperty("spring.profiles.active");
            } else {
                System.setProperty("spring.profiles.active", originalProfile);
            }
        }

        @Test
        @DisplayName("dev 프로파일에서는 형식이 틀려도 통과시킨다 - 현재 동작")
        void devProfileBypassesValidation() {
            System.setProperty("spring.profiles.active", "dev");

            assertThat(gateway.verifyWebhookBasicValidation("garbage", Map.of())).isTrue();
        }

        /**
         * 우회는 dev/local 로만 한정돼야 한다. prod 에서 살아나면 위조 웹훅이 기본 검증을 그냥 통과한다.
         */
        @Test
        @DisplayName("prod 프로파일에서는 우회하지 않는다")
        void prodProfileDoesNotBypass() {
            System.setProperty("spring.profiles.active", "prod");

            assertThat(gateway.verifyWebhookBasicValidation("garbage", Map.of())).isFalse();
        }

        @Test
        @DisplayName("dev 프로파일이어도 본문이 비면 거부한다 - 우회는 본문 검사 뒤에 온다")
        void devProfileStillRejectsEmptyBody() {
            System.setProperty("spring.profiles.active", "dev");

            assertThat(gateway.verifyWebhookBasicValidation("", Map.of())).isFalse();
        }
    }
}
