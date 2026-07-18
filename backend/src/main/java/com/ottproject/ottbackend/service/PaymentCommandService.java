package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.MembershipSubscribeRequestDto;
import com.ottproject.ottbackend.dto.PaymentCheckoutCreateRequestDto;
import com.ottproject.ottbackend.dto.PaymentCheckoutCreateSuccessResponseDto;
import com.ottproject.ottbackend.dto.PaymentMethodRegisterRequestDto;
import com.ottproject.ottbackend.dto.PaymentMethodResponseDto;
import com.ottproject.ottbackend.dto.PaymentWebhookEventDto;
import com.ottproject.ottbackend.dto.PaymentSucceededEventDto;
import com.ottproject.ottbackend.entity.IdempotencyKey;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.OutboxEvent;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.PaymentMethod;
import com.ottproject.ottbackend.entity.User;
import java.util.List;
import com.ottproject.ottbackend.enums.PaymentMethodType;
import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.repository.IdempotencyKeyRepository;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.OutboxEventRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.mybatis.PaymentQueryMapper;
import com.ottproject.ottbackend.service.ImportPaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PaymentCommandService
 *
 * 큰 흐름
 * - 결제 쓰기 흐름(체크아웃 생성, 웹훅 반영, 환불)을 처리한다.
 * - 게이트웨이 어댑터와 멱등키, 재검증 로직을 통해 안정성을 보장한다.
 *
 * 메서드 개요
 * - verifyWebhook: 웹훅 기본 검증
 * - parseWebhookPayload: 웹훅 페이로드 파싱
 * - checkout: 체크아웃 세션 생성(멱등키 저장 포함 가능)
 * - applyWebhookEvent: SUCCEEDED/FAILED/CANCELED/REFUNDED 상태 전이 및 구독 반영
 * - refundIfEligible: 24시간·시청<300초 정책 검증 후 환불 실행
 */
@Slf4j // 로깅
@Service // 스프링 빈 등록
@RequiredArgsConstructor // 생성자 주입
@Transactional // 쓰기 트랜잭션
public class PaymentCommandService { // 결제 쓰기 서비스
	private final MembershipPlanRepository membershipPlanRepository; // 플랜 리포지토리
	private final PaymentRepository paymentRepository; // 결제 리포지토리
	private final IdempotencyKeyRepository idempotencyKeyRepository; // 멱등키 리포지토리

	private final PaymentGateway paymentGateway; // 게이트웨이 어댑터(IMPORT 구현 주입)
	private final PlayerProgressReadService playerProgressReadService; // 플레이어 진행률 읽기 서비스(누적 시청 검증)
	private final MembershipSubscriptionRepository subscriptionRepository; // 구독 리포지토리(웹훅 전이 반영)
	private final PaymentQueryMapper paymentQueryMapper; // 결제 조회 매퍼
	private final PaymentMethodService paymentMethodService; // 결제수단 서비스
	private final MembershipCommandService membershipCommandService; // 멤버십 구독 생성(동기 직접 호출)
	private final OutboxEventRepository outboxEventRepository; // 아웃박스 이벤트 리포지토리(부수효과 발행)
	private final ObjectMapper objectMapper; // 이벤트 페이로드 JSON 직렬화

	// 테스트 결제 금액(원). 0이면 실제 플랜 금액으로 결제
	@Value("${payments.test-amount:0}")
	private long testAmount;
	
	/**
	 * 웹훅 메인 처리 로직
	 * - 아임포트로부터 수신된 웹훅을 처리하는 메인 진입점
	 */
	public void processWebhook(HttpHeaders headers, String rawBody) {
		log.info("웹훅 처리 시작");
		
		// 1. 기본 검증
		if (!verifyWebhook(headers, rawBody)) {
			log.error("웹훅 기본 검증 실패");
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook data");
		}
		
		// 2. 페이로드 파싱
		PaymentWebhookEventDto event = parseWebhookPayload(rawBody);
		log.info("웹훅 이벤트 파싱 완료 - merchant_uid: {}, status: {}", 
			event.providerSessionId, event.status);
		
		// 3. 데이터 검증
		validateWebhookData(event);
		
		// 3-1. 차액(proration) 결제는 전용 확정 경로(ProrationPaymentService.completeProrationPayment)가
		//       재검증·플랜 변경을 전담한다. 웹훅이 여기서 확정하면 markSucceededAndProvision이 '새 구독'을
		//       중복 생성하고, 클라 확정과 레이스(이미 처리된 결제)를 일으키므로 확인만 하고 무시한다(멱등, 200 OK).
		if (event.providerSessionId != null && event.providerSessionId.startsWith("proration_")) {
			log.info("차액 결제 웹훅 수신 - 전용 확정 경로가 처리하므로 스킵합니다. merchant_uid: {}", event.providerSessionId);
			return;
		}

		// 4. API 선재검증 후 전이 적용
		if (event.status == PaymentStatus.SUCCEEDED) {
			// merchant_uid로 기대 금액 조회
			Payment paymentForVerify = paymentQueryMapper.findByProviderSessionId(event.providerSessionId);
			if (paymentForVerify == null) {
				log.error("재검증을 위한 결제 조회 실패 - merchant_uid: {}", event.providerSessionId);
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다");
			}
			long expectedAmount = (paymentForVerify.getPrice() != null ? paymentForVerify.getPrice().getAmount() : 0L);
			boolean isValid = false;
			try {
				if (paymentGateway instanceof ImportPaymentGateway) {
					ImportPaymentGateway importGateway = (ImportPaymentGateway) paymentGateway;
					isValid = importGateway.verifyPaymentStatus(
						event.providerPaymentId,
						event.providerSessionId,
						expectedAmount
					);
				}
			} catch (Exception ex) {
				log.error("API 선재검증 중 예외 - imp_uid: {}, merchant_uid: {}", event.providerPaymentId, event.providerSessionId, ex);
				isValid = false;
			}
			if (!isValid) {
				log.error("API 선재검증 실패 - imp_uid: {}, merchant_uid: {}", event.providerPaymentId, event.providerSessionId);
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "웹훅 재검증 실패");
			}
		} else if (event.status == PaymentStatus.FAILED || event.status == PaymentStatus.CANCELED) {
			verifyNonSuccessWebhook(event);
		}

		// 5. 결제 이벤트 처리(모든 전이는 위 선재검증 통과 후 진행)
		processPaymentEvent(event);
		
		log.info("웹훅 처리 완료 - merchant_uid: {}", event.providerSessionId);
	}
	
	/**
	 * 웹훅 데이터 기본 검증
	 */
	private void validateWebhookData(PaymentWebhookEventDto event) {
		if (event.providerSessionId == null || event.providerSessionId.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "merchant_uid 누락");
		}
		if (event.status == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status 누락");
		}
		if (event.status == PaymentStatus.SUCCEEDED) {
			if (event.providerPaymentId == null || event.providerPaymentId.isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "성공 웹훅에 imp_uid 누락");
			}
			// amount는 웹훅에 없을 수 있으므로 필수로 강제하지 않음(아임포트 가이드에 따라 API 재검증으로 대조)
		}
	}
	
	/**
	 * 실패/취소 웹훅 재검증
	 * - 웹훅 주장만으로 상태를 바꾸면 위조 요청 하나로 구독을 PAST_DUE/해지 예약 상태로 떨어뜨릴 수 있다.
	 *   merchant_uid로 아임포트에 실제 상태를 역조회해 주장과 일치할 때만 전이를 허용한다.
	 * - 조회 불가/상태 불일치는 거부(fail-closed, SUCCEEDED 경로와 동일 정책).
	 *   정상 실패건의 연체 전환은 RecurringBillingService 가 청구 실패 시 자체적으로 수행하므로
	 *   여기서 거부해도 PAST_DUE 전이와 재시도(dunning)는 유실되지 않는다.
	 */
	private void verifyNonSuccessWebhook(PaymentWebhookEventDto event) {
		ImportPaymentGateway.ReconcileResult r = null;
		if (paymentGateway instanceof ImportPaymentGateway) {
			r = ((ImportPaymentGateway) paymentGateway).findByMerchantUid(event.providerSessionId); // 내부에서 예외를 흡수하고 found=false 반환
		}
		if (r == null || !r.found || mapIamportStatus(r.status) != event.status) {
			log.error("웹훅 재검증 실패 - merchant_uid: {}, 웹훅 주장: {}, 아임포트 실제: {}",
				event.providerSessionId, event.status, (r == null ? null : r.status));
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "웹훅 재검증 실패");
		}
	}

	/**
	 * 결제 이벤트 처리
	 */
	private void processPaymentEvent(PaymentWebhookEventDto event) {
		// merchant_uid로 결제 조회
		Payment payment = paymentQueryMapper.findByProviderSessionId(event.providerSessionId);
		if (payment == null) {
			log.error("결제를 찾을 수 없음 - merchant_uid: {}", event.providerSessionId);
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다");
		}
		
		log.info("결제 이벤트 처리 - paymentId: {}, 현재상태: {}, 웹훅상태: {}", 
			payment.getId(), payment.getStatus(), event.status);
		
		// 상태별 처리 (아임포트 권고: 웹훅 수신만으로 확정하지 말고, 성공 케이스는 API 재검증 수행)
		switch (event.status) {
			case SUCCEEDED:
				handlePaymentSuccess(payment, event);
				break;
			case FAILED:
				handlePaymentFailure(payment, event);
				break;
			case CANCELED:
				handlePaymentCancel(payment, event);
				break;
			case REFUNDED:
				handlePaymentRefund(payment, event);
				break;
			case PENDING:
				log.info("웹훅 사전 상태(PENDING) 수신 - merchant_uid: {}", event.providerSessionId);
				return;
			default:
				log.error("지원하지 않는 웹훅 상태 - status: {}", event.status);
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 상태입니다");
		}
	}
	
	/**
	 * 결제 성공 웹훅 처리
	 */
	private void handlePaymentSuccess(Payment payment, PaymentWebhookEventDto event) {
		log.info("결제 성공 웹훅 처리 - paymentId: {}", payment.getId());
		// processPaymentSuccess 대신 applyWebhookEvent를 직접 호출하여 일관성 보장
		applyWebhookEvent(payment.getId(), event);
	}
	
	/**
	 * 결제 실패 웹훅 처리
	 */
	private void handlePaymentFailure(Payment payment, PaymentWebhookEventDto event) {
		log.info("결제 실패 웹훅 처리 - paymentId: {}", payment.getId());
		applyWebhookEvent(payment.getId(), event);
	}
	
	/**
	 * 결제 취소 웹훅 처리
	 */
	private void handlePaymentCancel(Payment payment, PaymentWebhookEventDto event) {
		log.info("결제 취소 웹훅 처리 - paymentId: {}", payment.getId());
		applyWebhookEvent(payment.getId(), event);
	}
	
	/**
	 * 결제 환불 웹훅 처리
	 */
	private void handlePaymentRefund(Payment payment, PaymentWebhookEventDto event) {
		log.info("결제 환불 웹훅 처리 - paymentId: {}", payment.getId());
		applyWebhookEvent(payment.getId(), event);
	}

	/**
	 * 웹훅 기본 검증
	 */
	@Transactional(readOnly = true)
	public boolean verifyWebhook(HttpHeaders headers, String rawBody) {
		java.util.Map<String, String> map = new java.util.HashMap<>(); // 헤더 맵으로 변환
		headers.forEach((k, v) -> map.put(k, String.join(",", v))); // 다중값 결합
		return paymentGateway.verifyWebhookBasicValidation(rawBody, map); // 게이트웨이 위임
	}

	/**
	 * 웹훅 페이로드 파싱
	 * - 서명 검증 후에만 호출해야 합니다.
	 */
	@Transactional(readOnly = true)
	public com.ottproject.ottbackend.dto.PaymentWebhookEventDto parseWebhookPayload(String rawBody) {
		try {
			com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper(); // 파서
			@SuppressWarnings("unchecked")
			java.util.Map<String, Object> body = om.readValue(rawBody, java.util.Map.class); // Map으로 파싱
			if (body == null || body.isEmpty()) {
				throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Empty webhook payload");
			}
			// Iamport 원문 키가 존재하면 내부 DTO로 수동 매핑
			if (body.containsKey("imp_uid") || body.containsKey("merchant_uid") || body.containsKey("status")) {
				com.ottproject.ottbackend.dto.PaymentWebhookEventDto dto = new com.ottproject.ottbackend.dto.PaymentWebhookEventDto();
				dto.providerPaymentId = safeString(body.get("imp_uid"));
				dto.providerSessionId = safeString(body.get("merchant_uid"));
				dto.status = mapIamportStatus(safeString(body.get("status")));
				// 멱등키: 아임포트 웹훅에는 이벤트 ID가 없어 (결제, 상태) 조합으로 만든다.
				// imp_uid 단독으로 쓰면 정상적인 paid→cancelled 전이의 두 번째가 "이미 처리됨"으로 삼켜진다.
				if (dto.providerPaymentId != null && !dto.providerPaymentId.isBlank() && dto.status != null) {
					dto.eventId = dto.providerPaymentId + ":" + dto.status.name();
				}
				java.lang.Number amt = safeNumber(body.get("amount"));
				dto.amount = (amt == null ? null : amt.longValue());
				dto.currency = safeString(body.get("currency"));
				dto.receiptUrl = safeString(body.get("receipt_url"));
				return dto;
			}
			// 내부 포맷이면 그대로 바인딩
			return om.convertValue(body, com.ottproject.ottbackend.dto.PaymentWebhookEventDto.class);
		} catch (org.springframework.web.server.ResponseStatusException ex) {
			throw ex;
		} catch (Exception e) {
			throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid webhook payload"); // 400
		}
	}

	private com.ottproject.ottbackend.enums.PaymentStatus mapIamportStatus(String status) {
		if (status == null) return null;
		String s = status.trim().toLowerCase();
		switch (s) {
			case "paid":
				return com.ottproject.ottbackend.enums.PaymentStatus.SUCCEEDED;
			case "failed":
				return com.ottproject.ottbackend.enums.PaymentStatus.FAILED;
			case "cancelled":
			case "canceled":
				return com.ottproject.ottbackend.enums.PaymentStatus.CANCELED;
			case "ready":
				return com.ottproject.ottbackend.enums.PaymentStatus.PENDING;
			default:
				return null;
		}
	}

	private String safeString(Object v) {
		return v == null ? null : String.valueOf(v);
	}

	private java.lang.Number safeNumber(Object v) {
		if (v instanceof java.lang.Number) return (java.lang.Number) v;
		try {
			return v == null ? null : Long.parseLong(String.valueOf(v));
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 체크아웃 생성
	 */
	public PaymentCheckoutCreateSuccessResponseDto checkout(Long userId, PaymentCheckoutCreateRequestDto req) { // 체크아웃 생성
		if (req == null || req.planCode == null || req.planCode.isBlank()) { // 유효성 검사
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "플랜 코드가 필요합니다."); // 400
		}
		// 결제 서비스(pg) 매핑/검증: kakao|toss|nice 만 허용
		String normalizedService = (req.paymentService == null ? null : req.paymentService.trim().toLowerCase());
		String mappedPg = null;
		if (normalizedService == null || normalizedService.isBlank()) {
			mappedPg = "kakaopay.TC0ONETIME"; // 기본값: 카카오 원타임
		} else if ("kakao".equals(normalizedService)) {
			mappedPg = "kakaopay.TC0ONETIME";
		} else if ("toss".equals(normalizedService)) {
			mappedPg = "tosspayments";
		} else if ("nice".equals(normalizedService)) {
			mappedPg = "nice";
		} else {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 paymentService 입니다.");
		}
		
		log.info("결제 서비스 매핑 - input: {}, mapped: {}", req.paymentService, mappedPg);
		if (req.idempotencyKey != null && !req.idempotencyKey.isBlank() // 멱등키 전달 시
				&& idempotencyKeyRepository.findByKeyValue(req.idempotencyKey).isPresent()) { // 중복 확인
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 요청입니다."); // 409
		}
		MembershipPlan plan = membershipPlanRepository.findByCode(req.planCode) // 플랜 조회
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "플랜이 존재하지 않습니다.")); // 400

		long chargeAmount = (testAmount > 0 ? testAmount : plan.getPrice().getAmount()); // 테스트금액 우선

		// 결제수단 자동 등록 (아임포트 pay_method와 1:1 매핑)
		PaymentMethod paymentMethod = null;
		if (req.paymentService != null && !req.paymentService.isBlank()) {
			// 아임포트 pay_method와 1:1 매핑되는 타입 설정
			PaymentMethodType methodType;
			String brandUpper = null;
			if (normalizedService != null) {
				switch (normalizedService) {
					case "kakao":
						methodType = PaymentMethodType.KAKAO_PAY;
						brandUpper = null; // 간편결제는 brand 불필요
						break;
					case "toss":
						methodType = PaymentMethodType.TOSS_PAY;
						brandUpper = null; // 간편결제는 brand 불필요
						break;
					case "nice":
						methodType = PaymentMethodType.NICE_PAY;
						brandUpper = null; // 간편결제는 brand 불필요
						break;
					default:
						methodType = PaymentMethodType.CARD;
						// 카드 브랜드는 결제 성공 후 PG 응답(card_name)으로 확정 저장
						break;
				}
			} else {
				methodType = PaymentMethodType.CARD;
			}

			// 결제수단 등록
			PaymentMethodRegisterRequestDto methodDto = new PaymentMethodRegisterRequestDto();
			methodDto.provider = PaymentProvider.IMPORT;
			methodDto.type = methodType;
			methodDto.providerMethodId = "temp_" + System.currentTimeMillis(); // 임시 ID
			methodDto.brand = brandUpper; // ACCOUNT일 때만 세팅, CARD는 나중에 확정
			methodDto.isDefault = true; // 첫 결제수단이므로 기본으로 설정
			methodDto.priority = 100;
			methodDto.label = (methodType == PaymentMethodType.CARD ? "카드" : 
				methodType == PaymentMethodType.KAKAO_PAY ? "카카오페이" :
				methodType == PaymentMethodType.TOSS_PAY ? "토스페이" :
				methodType == PaymentMethodType.NICE_PAY ? "나이스페이" : "결제") + " 결제";
			
			paymentMethodService.register(userId, methodDto);
			
			// 등록된 결제수단 조회
			List<PaymentMethodResponseDto> methods = paymentMethodService.list(userId);
			if (!methods.isEmpty()) {
				PaymentMethodResponseDto latestMethod = methods.get(0);
				paymentMethod = new PaymentMethod();
				paymentMethod.setId(latestMethod.id);
			}
		}

		// 먼저 게이트웨이에서 세션 생성
		User user = new User();
		user.setId(userId);
		
		PaymentGateway.CheckoutSession session = paymentGateway.createCheckoutSession( // 게이트웨이 세션 생성 (prepare-only)
				user, // 사용자 정보
				plan, // 플랜 정보
				req.successUrl, // 성공 URL (웹훅/회계용 전달)
				req.cancelUrl, // 취소 URL (웹훅/회계용 전달)
				req.paymentService, // 선택 결제 서비스(프론트 SDK 매핑 용도만)
				chargeAmount // 실제 prepare 금액(테스트 시 1원 등)
		); // 세션 반환

		// 실제 세션 ID로 Payment 생성
		Payment payment = Payment.createPendingPayment(
				user, // 사용자 FK
				plan, // 플랜 FK
				PaymentProvider.IMPORT, // IMPORT 사용
				session.sessionId, // 실제 세션 ID
				new Money(chargeAmount, plan.getPrice().getCurrency()) // Money VO 사용
		);
		payment.setPaymentMethod(paymentMethod); // 결제수단 연결
		paymentRepository.save(payment); // 저장

		if (req.idempotencyKey != null && !req.idempotencyKey.isBlank()) { // 멱등키 저장
			idempotencyKeyRepository.save(IdempotencyKey.createIdempotencyKey(
					req.idempotencyKey, // 키
					"payment.checkout", // 용도
					"" // 응답 데이터 (빈 값)
			)); // 멱등 엔티티 생성
		}

		PaymentCheckoutCreateSuccessResponseDto res = new PaymentCheckoutCreateSuccessResponseDto(); // 응답 DTO
		res.redirectUrl = session.redirectUrl; // prepare-only 전환 이후 null (프론트 SDK가 결제창 호출)
		res.providerSessionId = session.sessionId; // merchant_uid(세션)
		res.amount = chargeAmount; // 결제 금액(검증용)
		res.paymentId = payment.getId(); // 내부 결제 ID
		res.pg = mappedPg; // 프론트 PG 코드 전달
		return res; // 반환
	}

	/**
	 * 웹훅 반영(멱등)
	 */
	public void applyWebhookEvent(Long paymentId, PaymentWebhookEventDto event) { // 웹훅 반영
		if (paymentId == null || event == null) { // 유효성 검사
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."); // 400
		}
		if (event.eventId != null && !event.eventId.isBlank() // 이벤트 멱등키
				&& idempotencyKeyRepository.findByKeyValue(event.eventId).isPresent()) { // 중복 확인
			return; // 이미 처리됨
		}
		Payment payment = paymentRepository.findById(paymentId) // 결제 단건 조회
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제가 존재하지 않습니다.")); // 400

		// 페이로드 재검증: 금액/통화/세션ID(가능 시) 대조
		if (event.amount != null && payment.getPrice() != null && event.amount.longValue() != payment.getPrice().getAmount()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount mismatch");
		}
		if (event.currency != null && payment.getPrice() != null && !event.currency.equals(payment.getPrice().getCurrency())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currency mismatch");
		}
		if (event.providerSessionId != null && payment.getProviderSessionId() != null && !event.providerSessionId.equals(payment.getProviderSessionId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "session mismatch");
		}

		LocalDateTime ts = event.occurredAt != null ? event.occurredAt : LocalDateTime.now(); // 타임스탬프

		if (event.status == PaymentStatus.SUCCEEDED) { // 성공
			// 공통 확정 로직으로 수렴(웹훅·클라이언트 확정·대사 배치가 동일 경로 사용, 멱등)
			markSucceededAndProvision(payment, event.providerPaymentId, event.receiptUrl, ts);

		} else if (event.status == PaymentStatus.FAILED) { // 실패
			payment.setStatus(PaymentStatus.FAILED); // 상태
			payment.setFailedAt(ts); // 시각
			// 구독 전이: 활성 구독이 있으면 PAST_DUE로 표시(즉시 경고 상태), 재시도는 배치가 수행
			subscriptionRepository.findActiveEffectiveByUser(payment.getUser().getId(), MembershipSubscriptionStatus.ACTIVE, ts)
					.ifPresent(sub -> {
						sub.setStatus(MembershipSubscriptionStatus.PAST_DUE); // 연체 상태 전환
						sub.setLastRetryAt(ts); // 최근 실패 시각 기록
					});

		} else if (event.status == PaymentStatus.CANCELED) { // 취소
			payment.setStatus(PaymentStatus.CANCELED); // 상태
			payment.setCanceledAt(ts); // 시각
			// 구독 전이: 자동갱신 중단 + 말일 해지 예약
			subscriptionRepository.findActiveEffectiveByUser(payment.getUser().getId(), MembershipSubscriptionStatus.ACTIVE, ts)
					.ifPresent(sub -> {
						sub.setAutoRenew(false); // 자동갱신 중단
						sub.setCancelAtPeriodEnd(true); // 말일 해지 예약
					});

		} else if (event.status == PaymentStatus.REFUNDED) { // 환불
			payment.setStatus(PaymentStatus.REFUNDED); // 상태
			payment.setRefundedAt(ts); // 시각
			payment.setRefundedAmount(event.amount); // 금액
			// 구독 전이: 환불 시 즉시 해지 처리(정책)
			subscriptionRepository.findActiveEffectiveByUser(payment.getUser().getId(), MembershipSubscriptionStatus.ACTIVE, ts)
					.ifPresent(sub -> {
						sub.setStatus(MembershipSubscriptionStatus.CANCELED); // 즉시 해지
						sub.setAutoRenew(false); // 자동갱신 중단
						sub.setCanceledAt(ts); // 해지 확정 시각
					});

		} else { // 방어
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 이벤트 상태입니다."); // 400
		}

		if (event.eventId != null && !event.eventId.isBlank()) { // 이벤트 멱등 저장
			idempotencyKeyRepository.save(IdempotencyKey.createIdempotencyKey(
					event.eventId, // 키
					"payment.webhook", // 용도
					null // 응답
			));
		}
	}

	/**
	 * 환불 정책 검증 후 환불 실행
	 * - 조건: 결제일로부터 7일 이내 AND 전혀 시청하지 않음
	 */
	public void refundIfEligible(Long userId, Long paymentId) { // 환불 엔드포인트 진입점
		Payment payment = paymentRepository.findById(paymentId) // 결제 단건 조회
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제가 존재하지 않습니다.")); // 400
		if (!payment.getUser().getId().equals(userId)) { // 소유자 검증
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 결제만 환불할 수 있습니다."); // 403
		}
		if (payment.getStatus() != PaymentStatus.SUCCEEDED) { // 성공 결제만 환불 대상
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "환불 대상 결제가 아닙니다."); // 400
		}
		if (payment.getPaidAt() == null) { // 안전체크
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제 완료 시간이 없습니다."); // 400
		}
		// 시간 조건: 7일 이내
		if (payment.getPaidAt().plusDays(7).isBefore(LocalDateTime.now())) { // 7일 초과
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "환불 가능 기간을 초과했습니다. (7일 이내만 환불 가능)"); // 400
		}
		// 시청 조건: 전혀 시청하지 않음 (1초라도 시청하면 환불 불가)
		int totalWatched = playerProgressReadService.sumWatchedSecondsSincePaidEpisodes(userId, payment.getPaidAt()); // 4화 이상 누적 시청 초 합
		if (totalWatched > 0) { // 1초라도 시청했으면 환불 불가
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "콘텐츠를 시청한 경우 환불이 불가합니다."); // 400
		}
		// 게이트웨이 환불 실행(전액 환불)
		try {
			PaymentGateway.RefundResult rr = paymentGateway.issueRefund(payment.getProviderPaymentId(), payment.getPrice().getAmount()); // 환불 호출
			payment.setStatus(PaymentStatus.REFUNDED); // 상태 전환
			payment.setRefundedAmount(payment.getPrice().getAmount()); // 전액 환불
			payment.setRefundedAt(rr.refundedAt != null ? rr.refundedAt : LocalDateTime.now()); // 환불 시각 기록
			paymentRepository.save(payment); // 저장
			
			// 환불 시 멤버십 구독 즉시 해지
			LocalDateTime now = LocalDateTime.now();
			subscriptionRepository.findActiveEffectiveByUser(userId, MembershipSubscriptionStatus.ACTIVE, now)
					.ifPresent(sub -> {
						sub.setStatus(MembershipSubscriptionStatus.CANCELED); // 즉시 해지
						sub.setAutoRenew(false); // 자동갱신 중단
						sub.setCanceledAt(now); // 해지 확정 시각
						subscriptionRepository.save(sub);
						log.info("환불로 인한 멤버십 구독 해지 - userId: {}, subscriptionId: {}", userId, sub.getId());
					});
			
			log.info("환불 성공 - paymentId: {}, imp_uid: {}", payment.getId(), payment.getProviderPaymentId());
		} catch (Exception e) {
			log.error("환불 실패 - paymentId: {}, imp_uid: {}", payment.getId(), payment.getProviderPaymentId(), e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "환불 처리 중 오류가 발생했습니다: " + e.getMessage());
		}
	}
	
	/**
	 * 결제 성공 확정 + 멤버십 지급 (공통 확정 로직)
	 * - 웹훅 / 클라이언트 확정 / 대사 배치가 모두 이 메서드로 수렴한다.
	 * - 멱등: 이미 SUCCEEDED면 아무 것도 하지 않아 중복 지급을 방지한다.
	 */
	private void markSucceededAndProvision(Payment payment, String providerPaymentId, String receiptUrl, LocalDateTime paidAt) {
		if (payment.getStatus() == PaymentStatus.SUCCEEDED) { // 이미 확정됨(멱등)
			return; // 재지급 방지
		}
		payment.setStatus(PaymentStatus.SUCCEEDED); // 상태 확정
		payment.setPaidAt(paidAt); // 결제 시각
		payment.setProviderPaymentId(providerPaymentId); // 외부 결제 ID(imp_uid)
		if (receiptUrl != null) { // 영수증은 있을 때만 갱신(클라 경로는 null일 수 있음)
			payment.setReceiptUrl(receiptUrl);
		}
		paymentRepository.save(payment); // 저장
		log.info("결제 SUCCEEDED 확정 - paymentId: {}, imp_uid: {}", payment.getId(), providerPaymentId);

		// PG 응답으로 결제수단 type/brand 최종 확정 (아임포트 pay_method와 1:1 매핑)
		try {
			if (paymentGateway instanceof ImportPaymentGateway) {
				ImportPaymentGateway importGateway = (ImportPaymentGateway) paymentGateway;
				ImportPaymentGateway.PaymentDetails details = importGateway.fetchPaymentDetails(providerPaymentId);
				PaymentMethod pm = payment.getPaymentMethod();
				if (pm != null) {
					pm.setProvider(PaymentProvider.IMPORT);
					String payMethod = details.payMethod == null ? "" : details.payMethod.trim().toLowerCase();
					switch (payMethod) { // pay_method와 1:1 매핑
						case "card":
							pm.setType(PaymentMethodType.CARD);
							String cardName = details.cardName;
							pm.setBrand(cardName != null && !cardName.isBlank() ? cardName.trim().toUpperCase() : "CARD");
							break;
						case "kakaopay":
							pm.setType(PaymentMethodType.KAKAO_PAY);
							pm.setBrand(null); // 간편결제는 brand 불필요
							break;
						case "tosspayments":
						case "toss":
							pm.setType(PaymentMethodType.TOSS_PAY);
							pm.setBrand(null); // 간편결제는 brand 불필요
							break;
						case "nice":
							pm.setType(PaymentMethodType.NICE_PAY);
							pm.setBrand(null); // 간편결제는 brand 불필요
							break;
						default:
							pm.setType(PaymentMethodType.CARD); // 기본값
							pm.setBrand("UNKNOWN");
					}
				}
			}
		} catch (Exception ex) {
			log.warn("결제수단 확정 중 세부정보 조회 실패 - imp_uid: {}", providerPaymentId, ex);
		}

		// 멤버십 구독 생성(동기·직접 호출): 실패 시 예외를 전파해 결제 확정과 함께 롤백하고 원인을 응답에 노출한다.
		// (과거: 이벤트 발행 + 리스너의 블랭킷 catch로 구독 생성 실패가 조용히 묻혀 결제만 SUCCEEDED로 남았음)
		try {
			MembershipSubscribeRequestDto subscribeDto = new MembershipSubscribeRequestDto();
			subscribeDto.planCode = payment.getMembershipPlan().getCode();
			membershipCommandService.subscribe(payment.getUser().getId(), subscribeDto);
			log.info("멤버십 구독 생성 완료 - userId: {}, planCode: {}", payment.getUser().getId(), subscribeDto.planCode);
		} catch (ResponseStatusException e) {
			throw e; // 이미 상태/사유가 있는 예외는 그대로 전파
		} catch (Exception e) {
			log.error("멤버십 구독 생성 실패 - paymentId: {}", payment.getId(), e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "멤버십 구독 생성 실패: " + e.getMessage(), e);
		}

		// [Kafka/Outbox] 결제 확정과 "같은 트랜잭션"으로 부수효과 이벤트를 아웃박스에 적재한다.
		// - 3경로(클라 확정/웹훅/재조정 배치)가 모두 이 메서드로 수렴하고 상단 멱등 가드(SUCCEEDED면 return)가 있어 정확히 1회만 적재된다.
		// - 실제 카프카 발행은 OutboxPublisher(폴링)가 담당하므로, 브로커 장애가 결제 확정을 막지 않는다.
		try {
			PaymentSucceededEventDto evt = new PaymentSucceededEventDto(
					UUID.randomUUID().toString(), // 이벤트 고유 식별자(컨슈머 멱등 키)
					payment.getId(),
					payment.getUser().getId(),
					payment.getMembershipPlan().getCode(),
					payment.getPrice() != null ? payment.getPrice().getAmount() : null,
					payment.getPaidAt()
			);
			OutboxEvent outbox = OutboxEvent.create(
					"Payment", // aggregateType
					String.valueOf(payment.getId()), // aggregateId
					"PaymentSucceeded", // eventType
					"payment.succeeded", // topic
					evt.getEventId(), // eventId
					objectMapper.writeValueAsString(evt) // payload(JSON)
			);
			outboxEventRepository.save(outbox);
			log.info("아웃박스 적재 완료 - eventId: {}, paymentId: {}", evt.getEventId(), payment.getId());
		} catch (Exception e) {
			// 아웃박스 적재는 결제 확정과 원자적이어야 한다(부수효과 유실 방지). 실패 시 함께 롤백 → 웹훅/배치 경로에서 재적재된다.
			log.error("아웃박스 이벤트 적재 실패 - paymentId: {}", payment.getId(), e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "아웃박스 이벤트 적재 실패: " + e.getMessage(), e);
		}
	}

	/**
	 * 클라이언트 결제 확정(동기 경로) — 현업 표준 이중 확인의 "포그라운드" 경로
	 * - 결제창 성공 콜백에서 imp_uid를 받아 아임포트 API로 재검증한 뒤 즉시 확정/지급한다.
	 * - 웹훅이 도달하지 않아도 멤버십이 활성화되도록 하는 주 경로(웹훅/배치는 백업 안전망).
	 * - 멱등: 이미 SUCCEEDED면 재검증 없이 성공으로 간주한다.
	 */
	public void completePayment(Long userId, Long paymentId, String impUid) {
		Payment payment = paymentRepository.findById(paymentId) // 결제 단건 조회
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "결제가 존재하지 않습니다.")); // 404
		if (!payment.getUser().getId().equals(userId)) { // 소유자 검증
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 결제만 확정할 수 있습니다."); // 403
		}
		if (payment.getStatus() == PaymentStatus.SUCCEEDED) { // 이미 확정됨(멱등)
			return;
		}
		if (impUid == null || impUid.isBlank()) { // imp_uid 필수
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imp_uid가 필요합니다."); // 400
		}
		long expectedAmount = (payment.getPrice() != null ? payment.getPrice().getAmount() : 0L); // 기대 금액(서버 확정, 테스트 1원)
		boolean valid = false;
		if (paymentGateway instanceof ImportPaymentGateway) { // 아임포트 API로 재검증(클라 응답 자체는 신뢰하지 않음)
			valid = ((ImportPaymentGateway) paymentGateway)
					.verifyPaymentStatus(impUid, payment.getProviderSessionId(), expectedAmount);
		}
		if (!valid) { // 재검증 실패
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제 검증에 실패했습니다. (PG 재검증 불일치)"); // 400
		}
		markSucceededAndProvision(payment, impUid, null, LocalDateTime.now()); // 공통 확정 로직으로 수렴
	}

	/**
	 * 대사(reconciliation) — 오래된 미확정(PENDING) 결제를 아임포트 실제 상태로 정리
	 * - 이중 확인(클라 확정/웹훅)이 모두 실패한 희귀 케이스까지 복구하는 최후 방어선.
	 * - PENDING은 imp_uid가 없으므로 merchant_uid로 역조회한다.
	 * - paid면 확정/지급(공통 로직 수렴), failed/cancelled면 상태 전이, 아직 미결이면 건너뜀.
	 * @return 상태가 확정적으로 정리되면 true
	 */
	public boolean reconcilePending(Long paymentId) {
		Payment payment = paymentRepository.findById(paymentId).orElse(null); // 결제 조회
		if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
			return false; // 대상 아님(이미 확정/취소됨)
		}
		// 차액(proration) 결제는 자체 complete 경로가 플랜 변경을 처리한다.
		// 여기서 확정하면 markSucceededAndProvision이 '새 구독'을 만들어 오처리되므로 건너뛴다.
		if (payment.getProviderSessionId() != null && payment.getProviderSessionId().startsWith("proration_")) {
			return false;
		}
		if (!(paymentGateway instanceof ImportPaymentGateway)) {
			return false; // 아임포트 구현이 아니면 스킵
		}
		ImportPaymentGateway.ReconcileResult r =
				((ImportPaymentGateway) paymentGateway).findByMerchantUid(payment.getProviderSessionId()); // merchant_uid 역조회
		if (!r.found || r.status == null) {
			return false; // 결제 시도 기록 없음(prepare만) → 유지
		}
		LocalDateTime now = LocalDateTime.now();
		switch (r.status) {
			case "paid":
				long expected = (payment.getPrice() != null ? payment.getPrice().getAmount() : 0L); // 서버 확정 금액(테스트 1원)
				if (r.amount != expected) {
					log.warn("대사 금액 불일치 - paymentId: {}, expected: {}, actual: {}", paymentId, expected, r.amount);
					return false; // 금액 불일치는 자동 확정하지 않음(수동 확인 대상)
				}
				markSucceededAndProvision(payment, r.impUid, r.receiptUrl, now); // 공통 확정 로직으로 수렴
				log.info("대사 배치로 결제 확정 - paymentId: {}", paymentId);
				return true;
			case "failed":
				payment.setStatus(PaymentStatus.FAILED);
				payment.setFailedAt(now);
				paymentRepository.save(payment);
				return true;
			case "cancelled":
			case "canceled":
				payment.setStatus(PaymentStatus.CANCELED);
				payment.setCanceledAt(now);
				paymentRepository.save(payment);
				return true;
			default:
				return false; // ready 등 미결 상태 → 유지
		}
	}

}
