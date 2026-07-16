package com.ottproject.ottbackend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TurnstileVerifier 단위 테스트
 *
 * 지키려는 규칙(봇 방어)
 * - secret 미설정 시에는 기능 자체가 비활성(no-op) → 통과. 키 없이 개발할 때 로그인/가입이 막히면 안 된다.
 * - secret 이 설정된 상태에서 토큰이 없으면 거부(검증 불가 = 통과시키면 방어가 무의미)
 * - Cloudflare 응답의 success 플래그를 그대로 따른다
 * - 클플 호출 자체가 실패하면 거부(fail-closed) — 장애를 우회 통로로 만들지 않는다
 */
@ExtendWith(MockitoExtension.class)
class TurnstileVerifierTest {

    @Mock
    private RestTemplate rest;

    private TurnstileVerifier verifierWithSecret(String secret) {
        TurnstileVerifier verifier = new TurnstileVerifier(rest);
        ReflectionTestUtils.setField(verifier, "secretKey", secret);
        return verifier;
    }

    // ===== 기능 비활성(no-op) =====

    @Test
    @DisplayName("secret 미설정이면 검증을 건너뛰고 통과 - 클플 호출조차 하지 않는다")
    void noSecretMeansDisabled() {
        TurnstileVerifier verifier = verifierWithSecret("");

        assertThat(verifier.verify("any-token")).isTrue();
        assertThat(verifier.verify(null)).isTrue(); // 토큰이 없어도 기능이 꺼져 있으면 통과
        verifyNoInteractions(rest);
    }

    @Test
    @DisplayName("secret 이 null 이어도 비활성으로 취급")
    void nullSecretMeansDisabled() {
        TurnstileVerifier verifier = verifierWithSecret(null);

        assertThat(verifier.verify("any-token")).isTrue();
        verifyNoInteractions(rest);
    }

    // ===== 토큰 검증 =====

    @Test
    @DisplayName("secret 이 있는데 토큰이 없으면 거부 - 검증 불가는 통과가 아니다")
    void missingTokenIsRejected() {
        TurnstileVerifier verifier = verifierWithSecret("secret-key");

        assertThat(verifier.verify(null)).isFalse();
        assertThat(verifier.verify("")).isFalse();
        assertThat(verifier.verify("   ")).isFalse();
        verify(rest, never()).postForObject(anyString(), any(), any());
    }

    @Test
    @DisplayName("클플이 success=true 면 통과")
    void cloudflareSuccessPasses() {
        TurnstileVerifier verifier = verifierWithSecret("secret-key");
        given(rest.postForObject(eq("https://challenges.cloudflare.com/turnstile/v0/siteverify"),
                any(HttpEntity.class), eq(Map.class)))
                .willReturn(Map.of("success", true));

        assertThat(verifier.verify("valid-token")).isTrue();
    }

    @Test
    @DisplayName("클플이 success=false 면 거부 - 위조/재사용 토큰")
    void cloudflareFailureIsRejected() {
        TurnstileVerifier verifier = verifierWithSecret("secret-key");
        given(rest.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .willReturn(Map.of("success", false,
                        "error-codes", java.util.List.of("timeout-or-duplicate")));

        assertThat(verifier.verify("reused-token")).isFalse();
    }

    @Test
    @DisplayName("클플 응답이 비어 있으면 거부")
    void nullResponseIsRejected() {
        TurnstileVerifier verifier = verifierWithSecret("secret-key");
        given(rest.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .willReturn(null);

        assertThat(verifier.verify("token")).isFalse();
    }

    // ===== 장애 대응 =====

    @Test
    @DisplayName("클플 호출이 실패하면 거부(fail-closed) - 장애가 우회 통로가 되면 안 된다")
    void networkErrorFailsClosed() {
        TurnstileVerifier verifier = verifierWithSecret("secret-key");
        given(rest.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .willThrow(new RestClientException("connection timed out"));

        assertThat(verifier.verify("token")).isFalse();
    }
}
