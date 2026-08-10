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
import lombok.extern.slf4j.Slf4j;

/**
 * ImportPaymentGateway
 *
 * 큰 흐름
 * - 아임포트 REST API와 연동하여 체크아웃 준비, 저장수단 재결제, 환불, 웹훅 기본검증을 수행한다.
 *
 * 메서드 개요
 * - createCheckoutSession: 결제 준비 등록 후 세션ID/리다이렉트URL 반환
 * - issueRefund: 환불 수행
 * - chargeWithSavedMethod: 저장 결제수단 재청구
 * - verifyWebhookBasicValidation: 웹훅 데이터 기본 유효성 검증
 * - getAccessToken: 토큰 발급
 * - verifyPayment: 아임포트 API로 결제 상태 재검증
 *
 * 인터페이스의 providerSessionId 는 아임포트의 merchant_uid, providerPaymentId 는 imp_uid 다.
 * 이 클래스 안에서는 아임포트 용어를 그대로 쓴다.
 */
@Component // 스프링 컴포넌트 등록
@Slf4j
public class ImportPaymentGateway implements PaymentGateway { // IMPORT 구현 시작
	@Value("${iamport.api.base:https://api.iamport.kr}")
	private String apiBase; // API Base

	@Value("${iamport.rest.api-key:}")
	private String apiKey; // REST API Key (application-*.yml: iamport.rest.api-key)

	@Value("${iamport.rest.api-secret:}")
	private String apiSecret; // REST API Secret (application-*.yml: iamport.rest.api-secret)

	private final RestTemplate rest; // REST 클라이언트 (Bean 주입)

	public ImportPaymentGateway(RestTemplate rest) {
		this.rest = rest;
	}

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

	/**
	 * 결제 사전 등록(prepare) — 특정 merchant_uid에 청구 금액을 고정한다.
	 * - 차액 결제처럼 자체 merchant_uid를 쓰는 흐름에서 재사용(메인 결제와 동일한 검증 경로 확보).
	 */
	@Override
	public void prepare(String merchantUid, long amount) {
		String token = getAccessToken(); // 토큰 발급
		HttpHeaders h = bearer(token); // 인증 헤더
		h.setContentType(MediaType.APPLICATION_JSON); // JSON 바디
		String prepareBody = String.format("{\"merchant_uid\":\"%s\",\"amount\":%d}", merchantUid, amount); // 준비 바디
		rest.exchange(apiBase + "/payments/prepare", HttpMethod.POST, new HttpEntity<>(prepareBody, h), String.class); // 결제 준비 등록
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

	/**
	 * 환불 여부 역조회 — GET /payments/{imp_uid}
	 *
	 * 왜 findPaymentBySessionId 를 재사용하지 않는가
	 * - 그쪽은 catch (Exception) → found=false 로 모든 조회 실패를 삼킨다. 여기서 그렇게 하면
	 *   네트워크 오류가 "환불 안 나감"으로 읽혀 선점이 풀리고 이중 환불이 난다.
	 * - 그쪽은 imp_uid 가 없는 PENDING 결제용이라 merchant_uid 로 친다. 환불 경로는 imp_uid 를
	 *   이미 들고 있으므로 단건 조회가 맞다.
	 *
	 * 아임포트는 환불을 status="cancelled" 로 표현한다. 판정할 수 없는 모든 경우가 UNKNOWN 이다.
	 */
	@Override
	public RefundStatus findRefundStatus(String providerPaymentId) {
		if (providerPaymentId == null || providerPaymentId.isBlank()) {
			return RefundStatus.UNKNOWN; // 조회할 식별자가 없다
		}
		try {
			String token = getAccessToken();
			HttpHeaders headers = bearer(token);
			ResponseEntity<java.util.Map> response = rest.exchange(
				apiBase + "/payments/" + providerPaymentId,
				HttpMethod.GET,
				new HttpEntity<>(headers),
				java.util.Map.class
			);
			if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
				log.warn("환불 역조회 응답 비정상 - imp_uid: {}, status: {}", providerPaymentId, response.getStatusCode());
				return RefundStatus.UNKNOWN;
			}
			@SuppressWarnings("unchecked")
			java.util.Map<String, Object> res = (java.util.Map<String, Object>) response.getBody().get("response");
			String status = (res == null ? null : (String) res.get("status"));
			if (status == null || !isValidStatus(status)) {
				log.warn("환불 역조회 상태 판정 불가 - imp_uid: {}, status: {}", providerPaymentId, status);
				return RefundStatus.UNKNOWN;
			}
			return "cancelled".equals(status) ? RefundStatus.REFUNDED : RefundStatus.NOT_REFUNDED;
		} catch (Exception e) {
			// 여기서 NOT_REFUNDED 로 떨어뜨리면 조회 실패가 곧 재환불 허용이 된다. 모르면 모른다고 답한다.
			log.warn("환불 역조회 실패 - imp_uid: {}", providerPaymentId, e);
			return RefundStatus.UNKNOWN;
		}
	}

	@Override
	public ChargeResult chargeWithSavedMethod(String providerCustomerId, String providerMethodId, String merchantUid, long amount, String currency, String description) { // 저장수단 청구
		String token = getAccessToken(); // 토큰(청구 요청 전이므로 여기서 실패하면 승인은 일어나지 않았다)
		HttpHeaders h = bearer(token); // 헤더
		h.setContentType(MediaType.APPLICATION_JSON); // JSON
		String body = String.format("{\"customer_uid\":\"%s\",\"merchant_uid\":\"%s\",\"amount\":%d,\"name\":\"%s\"}", providerMethodId, merchantUid, amount, description == null ? "Subscription" : description); // 바디
		ResponseEntity<String> res;
		try {
			res = rest.exchange(apiBase + "/subscribe/payments/again", HttpMethod.POST, new HttpEntity<>(body, h), String.class); // 호출
		} catch (org.springframework.web.client.HttpClientErrorException e) {
			// 4xx: 아임포트가 요청 자체를 거부했다. 승인은 일어나지 않았으므로 확정 실패로 취급해도 된다.
			throw new ChargeException(FailureType.SOFT_DECLINE, "HTTP_" + e.getStatusCode().value(), e.getMessage());
		} catch (org.springframework.web.client.RestClientException e) {
			// 타임아웃/커넥션 리셋/5xx: 요청은 나갔는데 결과를 모른다. 승인됐을 수도 있다.
			// 확정 실패로 단정하면 호출자가 다음 시도를 예약하고, 그 시도는 새 merchant_uid 라 그대로 또 청구된다.
			throw new ChargeException(FailureType.AMBIGUOUS, "NO_RESPONSE", "재청구 응답 확인 불가 - merchant_uid: " + merchantUid + ", " + e.getMessage());
		}

		String resBody = (res != null ? res.getBody() : null); // 바디 추출
		java.util.Map<String, Object> bodyMap = parseJsonToMap(resBody); // 응답 바디 맵

		// 아임포트는 논리적 실패(빌링키 없음 등)도 HTTP 200 + code != 0 + response: null 로 응답한다.
		// 여기서 걸러내지 않으면 실패한 청구가 imp_uid 없는 SUCCEEDED 결제로 저장되고 던닝이 영원히 돌지 않는다.
		Number code = (Number) bodyMap.get("code"); // 아임포트 논리 응답 코드
		String message = (String) bodyMap.get("message"); // 아임포트 실패 메시지
		if (code == null || code.intValue() != 0) {
			throw new ChargeException(
				FailureType.SOFT_DECLINE,
				code == null ? "UNKNOWN" : String.valueOf(code.intValue()),
				message == null ? "아임포트 재청구 실패" : message
			);
		}

		String status = (String) nested(bodyMap, "response", "status"); // 결제 상태
		String impUid = (String) nested(bodyMap, "response", "imp_uid"); // 외부 결제 ID
		if (!"paid".equals(status) || impUid == null || impUid.isBlank()) {
			throw new ChargeException(
				FailureType.SOFT_DECLINE,
				"NOT_PAID",
				message == null ? "아임포트 재청구가 완료되지 않음 - status: " + status : message
			);
		}

		ChargeResult result = new ChargeResult(); // 결과
		result.providerPaymentId = impUid; // 외부 결제 ID
		java.time.Instant paid = java.time.Instant.now(); // 간단 처리
		result.paidAt = java.time.LocalDateTime.ofInstant(paid, java.time.ZoneId.systemDefault()); // 지불 시각
		result.receiptUrl = (String) nested(bodyMap, "response", "receipt_url"); // 영수증 URL
		return result; // 반환
	}

	/**
	 * 빌링키 발급 여부 확인 — GET /subscribe/customers/{customer_uid}
	 *
	 * 없는 customer_uid 를 물으면 아임포트는 404 를 준다. 그래서 4xx 는 "발급 안 됨"이라는 답이고,
	 * 그 외 예외(타임아웃/5xx)는 "모름"이다. 둘 다 false 로 좁히는 것이 안전한 방향이다 —
	 * 이 값이 true 여야만 결제수단이 등록되므로, 틀려서 false 면 등록이 안 될 뿐이지만
	 * 틀려서 true 면 빌링키 없는 수단이 저장돼 자동 청구가 계속 거절당한다(그게 원래 있던 결함이다).
	 */
	@Override
	public boolean hasBillingKey(String customerUid) {
		if (customerUid == null || customerUid.isBlank()) {
			return false;
		}
		try {
			String token = getAccessToken();
			ResponseEntity<String> res = rest.exchange(
					apiBase + "/subscribe/customers/" + customerUid,
					HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
			java.util.Map<String, Object> bodyMap = parseJsonToMap(res != null ? res.getBody() : null);
			Number code = (Number) bodyMap.get("code");
			// 아임포트는 논리적 실패도 200 + code != 0 으로 주므로 code 와 response 를 함께 본다.
			return code != null && code.intValue() == 0 && nested(bodyMap, "response", "customer_uid") != null;
		} catch (org.springframework.web.client.HttpClientErrorException e) {
			log.info("빌링키 미발급 - customer_uid: {}, status: {}", customerUid, e.getStatusCode());
			return false;
		} catch (Exception e) {
			log.warn("빌링키 조회 실패(발급 안 된 것으로 취급) - customer_uid: {}", customerUid, e);
			return false;
		}
	}

	@Override
	public boolean verifyWebhookBasicValidation(String rawBody, java.util.Map<String, String> headers) { // 기본 검증
		// 웹훅 데이터의 기본 유효성만 검증
		if (rawBody == null || rawBody.isBlank()) {
			return false; // 바디 없음
		}
		
		// 개발 환경에서는 검증 우회 (실제 운영에서는 제거 필요)
		if (isDevelopmentEnvironment()) {
			return true;
		}
		
		// 포트원 웹훅 형식 검증
		try {
			java.util.Map<String, Object> webhookData = parseJsonToMap(rawBody);
			
			// 필수 필드 확인 (포트원 웹훅 형식)
			if (webhookData == null || webhookData.isEmpty()) {
				return false; // JSON 파싱 실패
			}
			
			// imp_uid, merchant_uid, status 필드 존재 여부 확인
			String impUid = (String) webhookData.get("imp_uid");
			String merchantUid = (String) webhookData.get("merchant_uid");
			String status = (String) webhookData.get("status");
			
			if (impUid == null || impUid.isBlank() || 
				merchantUid == null || merchantUid.isBlank() || 
				status == null || status.isBlank()) {
				return false; // 필수 필드 누락
			}
			
			// status 값 유효성 확인 (포트원 웹훅 상태값)
			if (!isValidStatus(status)) {
				return false; // 유효하지 않은 상태값
			}
			
			return true; // 모든 검증 통과
		} catch (Exception e) {
			return false; // JSON 파싱 실패 시 검증 실패
		}
	}

	/**
	 * 결제 상세 조회: pay_method/pg_provider/card_name 추출
	 */
	@Override
	public PaymentDetails fetchPaymentDetails(String impUid) {
		String token = getAccessToken();
		HttpHeaders headers = bearer(token);
		ResponseEntity<java.util.Map> response = rest.exchange(
			apiBase + "/payments/" + impUid,
			HttpMethod.GET,
			new HttpEntity<>(headers),
			java.util.Map.class
		);
		if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
			throw new IllegalStateException("Failed to fetch payment details from Iamport");
		}
		java.util.Map<String, Object> body = response.getBody();
		@SuppressWarnings("unchecked")
		java.util.Map<String, Object> res = (java.util.Map<String, Object>) body.get("response");
		PaymentDetails d = new PaymentDetails();
		d.payMethod = (String) (res == null ? null : res.get("pay_method"));
		d.pgProvider = (String) (res == null ? null : res.get("pg_provider"));
		d.cardName = (String) (res == null ? null : res.get("card_name"));
		return d;
	}

	/**
	 * merchant_uid로 결제 상태 역조회
	 * - GET /payments/find/{merchant_uid} 사용. 결제 시도가 없으면 found=false.
	 * - 아임포트 원문 status 는 여기서 ReconcileStatus 로 정규화한다(어휘를 아는 유일한 지점).
	 */
	@Override
	public ReconcileResult findPaymentBySessionId(String merchantUid) {
		ReconcileResult r = new ReconcileResult();
		try {
			String token = getAccessToken();
			HttpHeaders headers = bearer(token);
			ResponseEntity<java.util.Map> response = rest.exchange(
				apiBase + "/payments/find/" + merchantUid,
				HttpMethod.GET,
				new HttpEntity<>(headers),
				java.util.Map.class
			);
			if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
				return r; // found=false
			}
			@SuppressWarnings("unchecked")
			java.util.Map<String, Object> res = (java.util.Map<String, Object>) response.getBody().get("response");
			if (res == null) {
				return r; // 결제 시도 기록 없음(prepare만 된 상태)
			}
			r.found = true;
			r.status = toReconcileStatus((String) res.get("status"), merchantUid);
			r.providerPaymentId = (String) res.get("imp_uid");
			Number amt = (Number) res.get("amount");
			r.amount = (amt == null ? 0L : amt.longValue());
			r.receiptUrl = (String) res.get("receipt_url");
			return r;
		} catch (Exception e) {
			log.warn("merchant_uid로 결제 역조회 실패 - merchant_uid: {}", merchantUid, e);
			return r; // found=false
		}
	}
	
	/**
	 * 아임포트 원문 status → ReconcileStatus 정규화
	 *
	 * 대소문자 변환이나 trim 을 하지 않는다. 관대하게 받으면 "Paid" 같은 값이 PAID 로 읽혀 결제가 확정된다.
	 * 돈이 나가는 방향으로 추측하지 않고, 아는 값만 통과시키고 나머지는 UNKNOWN 으로 남긴다.
	 * canceled(l 하나) 는 아임포트가 보내지 않지만, 기존 대사 스위치가 받아주던 철자라 그대로 유지한다.
	 */
	private ReconcileStatus toReconcileStatus(String status, String merchantUid) {
		if (status == null) {
			log.warn("역조회 응답에 status 없음 - merchant_uid: {}", merchantUid);
			return ReconcileStatus.UNKNOWN;
		}
		switch (status) {
			case "ready":
				return ReconcileStatus.READY;
			case "paid":
				return ReconcileStatus.PAID;
			case "failed":
				return ReconcileStatus.FAILED;
			case "cancelled":
			case "canceled":
				return ReconcileStatus.CANCELLED;
			default:
				// 여기 걸린 결제는 아무도 확정하지 못해 대사 배치가 계속 다시 집는다. 로그가 유일한 단서다.
				log.warn("역조회 상태값 해석 불가 - merchant_uid: {}, status: {}", merchantUid, status);
				return ReconcileStatus.UNKNOWN;
		}
	}

	/**
	 * 포트원 웹훅 상태값 유효성 검증
	 */
	private boolean isValidStatus(String status) {
		// 포트원 웹훅에서 사용하는 상태값들
		return "ready".equals(status) ||      // 가상계좌 발급
			   "paid".equals(status) ||       // 결제 완료
			   "cancelled".equals(status) ||  // 결제 취소
			   "failed".equals(status);       // 결제 실패
	}
	
	/**
	 * 개발 환경 여부 확인
	 */
	private boolean isDevelopmentEnvironment() {
		String profile = System.getProperty("spring.profiles.active");
		if (profile == null) {
			profile = System.getenv("SPRING_PROFILES_ACTIVE");
		}
		return "dev".equals(profile) || "local".equals(profile);
	}

	/**
	 * 아임포트 API로 결제 상태 재검증
	 * - 웹훅 처리 후 실제 결제 상태를 API로 확인하여 보안 강화
	 */
	@Override
	public boolean verifyPayment(String impUid, String merchantUid, long expectedAmount) {
		try {
			String token = getAccessToken(); // 액세스 토큰 획득
			HttpHeaders headers = bearer(token); // 인증 헤더
			
			// 결제 상태 조회 API 호출
			String url = apiBase + "/payments/" + impUid;
			ResponseEntity<java.util.Map> response = rest.exchange(url, HttpMethod.GET, 
				new HttpEntity<>(headers), java.util.Map.class);
			
			if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
				return false; // API 호출 실패
			}
			
			java.util.Map<String, Object> paymentData = response.getBody();
			java.util.Map<String, Object> responseData = (java.util.Map<String, Object>) paymentData.get("response");
			
			if (responseData == null) {
				return false; // 응답 데이터 없음
			}
			
			// 결제 상태 확인
			String status = (String) responseData.get("status");
			if (!"paid".equals(status)) {
				return false; // 결제 완료 상태가 아님
			}
			
			// 금액 확인
			Number amount = (Number) responseData.get("amount");
			if (amount == null || amount.longValue() != expectedAmount) {
				return false; // 금액 불일치
			}
			
			// merchant_uid 확인
			String actualMerchantUid = (String) responseData.get("merchant_uid");
			if (!merchantUid.equals(actualMerchantUid)) {
				return false; // 주문번호 불일치
			}
			
			return true; // 모든 검증 통과
		} catch (Exception e) {
			// 카카오페이 테스트 채널(sandbox) 결제는 GET /payments/{imp_uid} 단건 조회가
			// 404("존재하지 않는 결제정보")를 반환하므로 merchant_uid 역조회로 폴백 검증한다.
			log.info("imp_uid 단건 조회 실패 → merchant_uid 폴백 검증 - imp_uid: {}, merchant_uid: {}", impUid, merchantUid);
			return verifyByMerchantUidFallback(impUid, merchantUid, expectedAmount);
		}
	}

	/**
	 * merchant_uid 역조회 기반 폴백 검증
	 * - imp_uid 단건 조회가 불가한 결제(카카오페이 sandbox 등)를 실시간으로 검증하기 위해 사용.
	 */
	private boolean verifyByMerchantUidFallback(String impUid, String merchantUid, long expectedAmount) {
		if (merchantUid == null || merchantUid.isBlank()) {
			return false; // 역조회 불가
		}
		ReconcileResult r = findPaymentBySessionId(merchantUid);
		if (!r.found || r.status != ReconcileStatus.PAID) {
			return false; // 결제 완료 상태가 아님
		}
		if (r.amount != expectedAmount) {
			return false; // 금액 불일치
		}
		if (impUid != null && !impUid.equals(r.providerPaymentId)) {
			return false; // imp_uid 불일치(위조 방어)
		}
		return true; // 검증 통과
	}

	private static String firstNonEmpty(java.util.Map<String, String> headers, String... keys) { // 첫 유효 헤더값
		for (String k : keys) { // 후보 순회
			String v = headers.get(k); // 값 조회
			if (v != null && !v.isBlank()) return v; // 반환
		}
		return null; // 없음
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


