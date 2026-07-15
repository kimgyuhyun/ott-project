package com.ottproject.ottbackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * TurnstileVerifier
 *
 * 큰 흐름
 * - Cloudflare Turnstile 토큰을 서버(백엔드)에서 siteverify API로 검증한다.
 * - 사람(정상 사용자)인지 판별해 봇 자동화(브루트포스/인증코드 메일 남용)를 차단한다.
 * - 검증은 반드시 서버에서 한다: 봇은 프론트를 거치지 않고 API를 직접 호출하기 때문.
 *
 * 동작 정책
 * - secret-key 미설정 시: 기능 비활성(no-op) → 항상 통과(true). (키 넣기 전 개발 환경에서 로그인/가입을 막지 않음)
 * - 토큰이 비어 있으면: 검증 불가 → 거부(false)
 * - siteverify 응답의 success 플래그를 그대로 반환
 * - 네트워크 오류 등 검증 자체가 불가하면: 안전 우선으로 거부(false, fail-closed)
 *
 * 네트워크 전제
 * - 백엔드(app) 컨테이너는 egress 브리지에 연결되어 외부 인터넷이 가능하다(OAuth/메일/결제와 동일 경로).
 *   따라서 challenges.cloudflare.com 호출이 채굴기 방어(egress 차단) 설계와 충돌하지 않는다.
 */
@Service
@Slf4j
public class TurnstileVerifier {

    // Cloudflare Turnstile 서버 검증 엔드포인트
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    @Value("${turnstile.secret-key:}")
    private String secretKey; // 백엔드 전용 비밀 키(절대 노출 금지). 미설정 시 기능 비활성.

    private final RestTemplate rest; // 기존 RestTemplateConfig 의 Bean 재사용(타임아웃 설정 포함)

    public TurnstileVerifier(RestTemplate rest) {
        this.rest = rest;
    }

    /**
     * Turnstile 토큰 검증
     *
     * @param token 프론트 위젯이 발급한 cf-turnstile-response 토큰(없을 수 있음)
     * @return 사람으로 확인되면 true, 아니면 false
     */
    public boolean verify(String token) {
        // secret 미설정 → 기능 비활성. 키를 넣기 전(개발)엔 인증 흐름을 막지 않는다.
        if (secretKey == null || secretKey.isBlank()) {
            return true;
        }
        // 토큰이 없으면 검증 불가 → 거부
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED); // siteverify 는 form 인코딩 요구
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("secret", secretKey);   // 비밀 키
            body.add("response", token);      // 검증할 토큰

            Map<?, ?> result = rest.postForObject(VERIFY_URL, new HttpEntity<>(body, headers), Map.class);
            boolean success = result != null && Boolean.TRUE.equals(result.get("success"));
            if (!success) {
                // error-codes 예: invalid-input-response(위조/재사용), timeout-or-duplicate 등
                log.warn("Turnstile 검증 실패 - result={}", result);
            }
            return success;
        } catch (Exception e) {
            // 클플 도달 불가/타임아웃 등: 안전하게 거부(fail-closed)
            log.warn("Turnstile 검증 호출 오류 - {}", e.getMessage());
            return false;
        }
    }
}
