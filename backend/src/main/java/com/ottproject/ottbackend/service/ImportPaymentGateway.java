package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * ImportPaymentGateway
 *
 * 큰 흐름
 * - 아임포트 REST API와 연동하여 체크아웃 준비, 저장수단 재결제, 환불, 웹훅 서명검증을 수행한다.
 *
 * 메서드 개요
 * - createCheckoutSession: 결제 준비 등록 후 세션ID/리다이렉트URL 반환
 * - issueRefund: 환불 수행
 * - chargeWithSavedMethod: 저장 결제수단 재청구
 * - verifyWebhookSignature: X-Iamport-Signature 검증(HMAC-SHA256)
 * - getAccessToken: 토큰 발급
 */
@Component // 스프링 컴포넌트 등록
public class ImportPaymentGateway implements PaymentGateway { // IMPORT 구현 시작
	@Value("${iamport.api.base:https://api.iamport.kr}")
	private String apiBase; // API Base

	@Value("${iamport.rest.api-key:}")
	private String apiKey; // REST API Key (application-*.yml: iamport.rest.api-key)

	@Value("${iamport.rest.api-secret:}")
	private String apiSecret; // REST API Secret (application-*.yml: iamport.rest.api-secret)

	@Value("${iamport.webhook.secret:}")
	private String webhookSecret; // 웹훅 서명 비밀키(선택)

	private final RestTemplate rest = new RestTemplate(); // REST 클라이언트

	@Override // 인터페이스 구현
	public CheckoutSession createCheckoutSession(User user, MembershipPlan plan, String successUrl, String cancelUrl, String paymentService, long amount) { // 세션 생성(prepare-only)
		String token = getAccessToken(); // 토큰 발급
		String merchantUid = "order_" + System.currentTimeMillis(); // 고유 주문번호
		HttpHeaders h = bearer(token); // 인증 헤더
		h.setContentType(MediaType.APPLICATION_JSON); // JSON 바디
		// NOTE: prepare-only: 서버는 /payments/prepare로 금액을 고정만 합니다. 실제 결제창 호출은 프론트 JS SDK가 수행합니다.
		// dev 환경에서 payments.test-amount가 설정되면 PaymentCommandService에서 전달된 amount(예: 1원)로 prepare합니다.
		String prepareBody = String.format("{\"merchant_uid\":\"%s\",\"amount\":%d}", merchantUid, amount); // 준비 바디
		rest.exchange(apiBase + "/payments/prepare", HttpMethod.POST, new HttpEntity<>(prepareBody, h), String.class); // 결제 준비 등록

		CheckoutSession session = new CheckoutSession(); // 반환 객체 생성
		session.sessionId = merchantUid; // 세션 ID
		// prepare-only 전환: 백엔드는 결제창 URL을 조립하지 않습니다. 프론트가 JS SDK로 호출합니다.
		session.redirectUrl = null; // 사용하지 않음
		return session; // 반환
	}

	@Override // 인터페이스 구현
	public RefundResult issueRefund(String providerPaymentId, long amount) { // 환불 실행
		String token = getAccessToken(); // 토큰 획득
		HttpHeaders h = bearer(token); // 인증 헤더
		h.setContentType(MediaType.APPLICATION_JSON); // JSON 바디
		String body = String.format("{\"imp_uid\":\"%s\",\"amount\":%d}", providerPaymentId, amount); // 환불 바디
		rest.exchange(apiBase + "/payments/cancel", HttpMethod.POST, new HttpEntity<>(body, h), java.util.Map.class); // 호출

		RefundResult result = new RefundResult(); // 결과
		result.providerRefundId = providerPaymentId; // 결제 imp_uid 사용
		result.refundedAt = java.time.LocalDateTime.now(); // 간단 처리
		return result; // 반환
	}

	@Override
	public ChargeResult chargeWithSavedMethod(String providerCustomerId, String providerMethodId, long amount, String currency, String description) { // 저장수단 청구
		String token = getAccessToken(); // 토큰
		HttpHeaders h = bearer(token); // 헤더
		h.setContentType(MediaType.APPLICATION_JSON); // JSON
		String merchantUid = "rebill_" + System.currentTimeMillis(); // 고유 주문번호
		String body = String.format("{\"customer_uid\":\"%s\",\"merchant_uid\":\"%s\",\"amount\":%d,\"name\":\"%s\"}", providerMethodId, merchantUid, amount, description == null ? "Subscription" : description); // 바디
		ResponseEntity<String> res = rest.exchange(apiBase + "/subscribe/payments/again", HttpMethod.POST, new HttpEntity<>(body, h), String.class); // 호출

		ChargeResult result = new ChargeResult(); // 결과
		String resBody = (res != null ? res.getBody() : null); // 바디 추출
		java.util.Map<String, Object> bodyMap = parseJsonToMap(resBody); // 응답 바디 맵
		result.providerPaymentId = (String) nested(bodyMap, "response", "imp_uid"); // 외부 결제 ID
		java.time.Instant paid = java.time.Instant.now(); // 간단 처리
		result.paidAt = java.time.LocalDateTime.ofInstant(paid, java.time.ZoneId.systemDefault()); // 지불 시각
		result.receiptUrl = (String) nested(bodyMap, "response", "receipt_url"); // 영수증 URL
		return result; // 반환
	}

	@Override
	public boolean verifyWebhookSignature(String rawBody, java.util.Map<String, String> headers) { // 서명 검증
		if (webhookSecret == null || webhookSecret.isBlank()) { // 비밀키 미설정 시 우회
			return true; // 테스트/개발 모드 패스
		}
		String sig = firstNonEmpty(headers, "X-Iamport-Signature", "X-Imp-Signature", "X-Import-Signature"); // 헤더 후보
		if (sig == null || sig.isBlank()) return false; // 헤더 없음
		String computed = hmacSha256Hex(rawBody, webhookSecret); // HMAC 계산
		return sig.equalsIgnoreCase(computed); // 비교
	}

	private static String firstNonEmpty(java.util.Map<String, String> headers, String... keys) { // 첫 유효 헤더값
		for (String k : keys) { // 후보 순회
			String v = headers.get(k); // 값 조회
			if (v != null && !v.isBlank()) return v; // 반환
		}
		return null; // 없음
	}

	private static String hmacSha256Hex(String data, String secret) { // HMAC-SHA256(hex)
		try {
			Mac mac = Mac.getInstance("HmacSHA256"); // 알고리즘
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")); // 키 설정
			byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8)); // 계산
			StringBuilder sb = new StringBuilder(out.length * 2); // hex 버퍼
			for (byte b : out) { // 바이트 순회
				sb.append(String.format("%02x", b)); // 2자리 hex
			}
			return sb.toString(); // 결과 반환
		} catch (Exception e) {
			return ""; // 실패 시 빈 문자열
		}
	}

	private String getAccessToken() { // 액세스 토큰 획득
		HttpHeaders headers = new HttpHeaders(); // 헤더
		headers.setContentType(MediaType.APPLICATION_JSON); // JSON
		String body = String.format("{\"imp_key\":\"%s\",\"imp_secret\":\"%s\"}", apiKey, apiSecret); // 바디
		ResponseEntity<TokenResponse> res = rest.exchange(apiBase + "/users/getToken", HttpMethod.POST, new HttpEntity<>(body, headers), TokenResponse.class); // 호출
		TokenResponse tr = (res != null ? res.getBody() : null); // 응답 바디
		if (res == null || !res.getStatusCode().is2xxSuccessful() || tr == null || tr.response == null) {
			throw new IllegalStateException("Failed to get Iamport access token"); // 실패
		}
		return tr.response.access_token; // 토큰
	}

	private HttpHeaders bearer(String token) { // 인증 헤더 생성
		HttpHeaders h = new HttpHeaders(); // 헤더
		h.setBearerAuth(token); // Bearer
		return h; // 반환
	}

	private Object nested(java.util.Map<?,?> map, String... keys) { // 중첩 Map 안전 접근
		Object cur = map; // 현재 커서
		for (String k : keys) { // 키 순회
			if (!(cur instanceof java.util.Map)) return null; // 맵 아님
			cur = ((java.util.Map<?,?>) cur).get(k); // 접근
			if (cur == null) return null; // 없음
		}
		return cur; // 값 반환
	}

	private java.util.Map<String, Object> parseJsonToMap(String json) { // JSON 문자열 → Map 변환
		try {
			if (json == null || json.isBlank()) return java.util.Collections.emptyMap(); // 빈 맵
			com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper(); // 매퍼 생성
			@SuppressWarnings("unchecked")
			java.util.Map<String, Object> m = om.readValue(json, java.util.Map.class); // 파싱
			return m == null ? java.util.Collections.emptyMap() : m; // 널 가드
		} catch (Exception e) {
			return java.util.Collections.emptyMap(); // 실패 시 빈 맵
		}
	}

	@SuppressWarnings("unused")
	private static class TokenResponse { // /users/getToken 응답 매핑
		public int code; // 상태코드
		public String message; // 메시지
		public Token response; // 실제 응답
	}

	@SuppressWarnings("unused")
	private static class Token { // 토큰 바디
		public String access_token; // 액세스 토큰
		public long now; // 서버 시간
		public long expired_at; // 만료 시각(epoch)
	}
}


