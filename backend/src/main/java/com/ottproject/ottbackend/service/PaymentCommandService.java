package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.MembershipSubscribeRequestDto;
import com.ottproject.ottbackend.dto.PaymentCheckoutCreateRequestDto;
import com.ottproject.ottbackend.dto.PaymentCheckoutCreateSuccessResponseDto;
import com.ottproject.ottbackend.dto.PaymentWebhookEventDto;
import com.ottproject.ottbackend.dto.PaymentSucceededEventDto;
import com.ottproject.ottbackend.entity.IdempotencyKey;
import com.ottproject.ottbackend.exception.DuplicateWebhookEventException;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.OutboxEvent;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.PaymentMethod;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.IdempotencyKeyStatus;
import com.ottproject.ottbackend.enums.PaymentMethodType;
import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.repository.IdempotencyKeyRepository;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.OutboxEventRepository;
import com.ottproject.ottbackend.repository.PaymentMethodRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.mybatis.PaymentQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
 * - checkout: 체크아웃 세션 생성(멱등키 선삽입으로 중복 차단)
 * - applyWebhookEvent: SUCCEEDED/FAILED/CANCELED/REFUNDED 상태 전이 및 구독 반영
 * - refundIfEligible: 24시간·시청<300초 정책 검증 후 환불 실행
 *
 * 결제 확정의 이중 지급 방어 구조
 * - 확정 경로는 셋이다: 클라 확정(completePayment) / 웹훅(applyWebhookEvent) / 대사 배치(reconcilePending).
 *   셋 다 markSucceededAndProvision 으로 수렴하고, 그 멱등 가드는 "이미 SUCCEEDED 면 return" 이다.
 * - 이 가드가 유효하려면 상태를 "잠그고" 읽어야 한다. 락 없이 읽으면 클라 확정과 웹훅이
 *   (설계상 동시에 일어나는 정상 흐름이다) 둘 다 가드를 통과해 멤버십을 두 번 지급한다.
 * - 그래서 세 경로 모두 "PG 재검증(트랜잭션 밖) → 락+상태 재확인+지급(짧은 트랜잭션)" 으로 쪼갠다.
 *   락 구간에 외부 API 호출을 넣지 않기 위한 분리다(9절). RecurringBillingService.retryBilling 과 같은 형태.
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
	private final PaymentMethodRepository paymentMethodRepository; // 빌링키 확인 후 저장 결제수단 등록/조회
	private final MembershipCommandService membershipCommandService; // 멤버십 구독 생성(동기 직접 호출)
	private final RecurringBillingService recurringBillingService; // 재청구 결제의 대사 확정(구독 연장 로직이 체크아웃과 다름)
	private final OutboxEventRepository outboxEventRepository; // 아웃박스 이벤트 리포지토리(부수효과 발행)
	private final ObjectMapper objectMapper; // 이벤트 페이로드 JSON 직렬화

	// 단계별 트랜잭션을 프록시에 태우기 위한 자기 참조(RecurringBillingService 와 같은 이유).
	// 확정 경로는 "PG 재검증 / 락+재확인+지급" 을 서로 다른 트랜잭션 경계로 나눠야 하는데,
	// 같은 빈 안에서 그냥 호출하면 프록시를 안 타서 @Transactional 이 무시된다.
	@Autowired
	@Lazy
	private PaymentCommandService self;

	// 테스트 결제 금액(원). 0이면 실제 플랜 금액으로 결제
	@Value("${payments.test-amount:0}")
	private long testAmount;
	
	/**
	 * 웹훅 메인 처리 로직
	 * - 아임포트로부터 수신된 웹훅을 처리하는 메인 진입점
	 * - 트랜잭션을 열지 않는다: 아래 4단계의 API 선재검증이 외부 HTTP 호출이라 트랜잭션 안에 두면
	 *   PG 응답 시간만큼 트랜잭션이 늘어진다(4·5절). 실제 상태 변경은 applyWebhookEvent 가
	 *   자기 트랜잭션 안에서 락을 잡고 수행한다.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
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
				isValid = paymentGateway.verifyPayment(
					event.providerPaymentId,
					event.providerSessionId,
					expectedAmount
				);
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
		PaymentGateway.ReconcileResult r =
				paymentGateway.findPaymentBySessionId(event.providerSessionId); // 내부에서 예외를 흡수하고 found=false 반환
		if (!r.found || toPaymentStatus(r.status) != event.status) {
			log.error("웹훅 재검증 실패 - merchant_uid: {}, 웹훅 주장: {}, 아임포트 실제: {}",
				event.providerSessionId, event.status, r.status);
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
		// self 경유: processWebhook 에는 트랜잭션이 없으므로 여기서 프록시를 타야 경계가 생긴다.
		self.applyWebhookEvent(payment.getId(), event);
	}
	
	/**
	 * 결제 실패 웹훅 처리
	 */
	private void handlePaymentFailure(Payment payment, PaymentWebhookEventDto event) {
		log.info("결제 실패 웹훅 처리 - paymentId: {}", payment.getId());
		self.applyWebhookEvent(payment.getId(), event);
	}
	
	/**
	 * 결제 취소 웹훅 처리
	 */
	private void handlePaymentCancel(Payment payment, PaymentWebhookEventDto event) {
		log.info("결제 취소 웹훅 처리 - paymentId: {}", payment.getId());
		self.applyWebhookEvent(payment.getId(), event);
	}
	
	/**
	 * 결제 환불 웹훅 처리
	 */
	private void handlePaymentRefund(Payment payment, PaymentWebhookEventDto event) {
		log.info("결제 환불 웹훅 처리 - paymentId: {}", payment.getId());
		self.applyWebhookEvent(payment.getId(), event);
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

	/**
	 * 역조회 상태 → 내부 결제 상태
	 * - UNKNOWN 은 null 로 남긴다. 어떤 웹훅 주장과도 일치하지 않으므로 재검증이 거부된다(fail-closed).
	 * - 웹훅 원문(문자열) 파싱에는 mapIamportStatus 를 쓴다. 그쪽은 게이트웨이를 거치지 않은 값이다.
	 */
	private static PaymentStatus toPaymentStatus(PaymentGateway.ReconcileStatus status) {
		if (status == null) return null;
		switch (status) {
			case PAID:
				return PaymentStatus.SUCCEEDED;
			case FAILED:
				return PaymentStatus.FAILED;
			case CANCELLED:
				return PaymentStatus.CANCELED;
			case READY:
				return PaymentStatus.PENDING;
			default:
				return null; // UNKNOWN
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
		// 카카오페이만 정기결제 채널(TCSUBSCRIP)이 있다. 토스·나이스는 공용 테스트 상점아이디에
		// 정기결제가 없어 빌링키를 발급받을 수 없으므로 단건 채널 그대로 둔다 — 그 대신 아래에서
		// 저장 결제수단을 만들지 않는다. 빌링키 없이 결제수단만 있으면 자동 청구가 영원히 거절당한다.
		if (normalizedService == null || normalizedService.isBlank()) {
			mappedPg = "kakaopay.TCSUBSCRIP"; // 기본값: 카카오 정기결제
		} else if ("kakao".equals(normalizedService)) {
			mappedPg = "kakaopay.TCSUBSCRIP";
		} else if ("toss".equals(normalizedService)) {
			mappedPg = "tosspayments";
		} else if ("nice".equals(normalizedService)) {
			mappedPg = "nice";
		} else {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 paymentService 입니다.");
		}
		
		log.info("결제 서비스 매핑 - input: {}, mapped: {}", req.paymentService, mappedPg);
		// 멱등키 선삽입: 처리를 시작하기 전에 먼저 넣어 유니크 제약이 동시 요청을 판정하게 한다.
		//
		// 예전에는 여기서 findByKeyValue 로 확인만 하고(select-then-insert), 플랜 조회·결제수단 등록·
		// PG 세션 생성·Payment 생성을 다 끝낸 뒤 맨 마지막에 키를 저장했다. 확인과 저장 사이가
		// 그만큼 벌어져 있어 동시 요청 둘 다 확인을 통과했고, PENDING 결제와 PG 세션이 두 개 생겼다
		// (막아줄 제약이 payments.provider_session_id 뿐인데 세션 ID 는 서로 다르게 발급된다).
		//
		// saveAndFlush 로 INSERT 를 여기서 확정시켜, 제약 위반을 이 자리에서 잡는다. 중복은 기존과 똑같이
		// 409 다 — 해지(membership.cancel)처럼 성공으로 흡수하지 않는다. 체크아웃의 응답은 결제 ID 와
		// PG 세션이라 첫 요청의 결과를 뒤늦게 재현해 줄 수 없고, 원래 계약도 409 였다.
		//
		// 대가: 같은 키의 두 번째 요청은 첫 요청이 끝날 때까지 유니크 인덱스에서 대기한다.
		// checkout 은 트랜잭션 안에서 PG 세션을 만들므로 그 대기가 PG 응답 시간만큼이다.
		// 중복 요청이 결과를 알려면 기다리는 수밖에 없으므로 이는 정상 동작이다(해지 경로도 동일).
		// 멱등키 선삽입: 처리를 시작하기 전에 먼저 넣어 유니크 제약이 동시 요청을 판정하게 한다.
		//
		// 예전에는 여기서 findByKeyValue 로 확인만 하고(select-then-insert), 플랜 조회·결제수단 등록·
		// PG 세션 생성·Payment 생성을 다 끝낸 뒤 맨 마지막에 키를 저장했다. 확인과 저장 사이가
		// 그만큼 벌어져 있어 동시 요청 둘 다 확인을 통과했고, PENDING 결제와 PG 세션이 두 개 생겼다
		// (막아줄 제약이 payments.provider_session_id 뿐인데 세션 ID 는 요청마다 다르게 발급된다).
		//
		// saveAndFlush 로 INSERT 를 여기서 확정시켜, 제약 위반을 이 자리에서 잡는다. 중복은 기존과 똑같이
		// 409 다 — 해지(membership.cancel)처럼 성공으로 흡수하지 않는다. 체크아웃의 응답은 결제 ID 와
		// PG 세션이라 첫 요청의 결과를 뒤늦게 재현해 줄 수 없고, 원래 계약도 409 였다.
		//
		// 대가: 같은 키의 두 번째 요청은 첫 요청이 끝날 때까지 유니크 인덱스에서 대기한다.
		// checkout 은 트랜잭션 안에서 PG 세션을 만들므로 그 대기가 PG 응답 시간만큼이다.
		// 중복 요청이 결과를 알려면 기다리는 수밖에 없으므로 이는 정상 동작이다(해지 경로도 동일).
		if (req.idempotencyKey != null && !req.idempotencyKey.isBlank()) { // 멱등키 전달 시
			if (idempotencyKeyRepository.findByKeyValue(req.idempotencyKey).isPresent()) { // 빠른 경로: 한참 전에 처리된 키
				throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 요청입니다."); // 409
			}
			try {
				idempotencyKeyRepository.saveAndFlush(IdempotencyKey.createIdempotencyKey(
						req.idempotencyKey, // 키
						"payment.checkout", // 용도
						"", // 응답 데이터 (빈 값)
						LocalDateTime.now() // 적재 시각
				));
			} catch (DataIntegrityViolationException e) { // 동시 요청 경합에서 진 쪽
				throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 요청입니다.", e); // 409(빠른 경로와 같은 응답)
			}
		}
		MembershipPlan plan = membershipPlanRepository.findByCode(req.planCode) // 플랜 조회
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "플랜이 존재하지 않습니다.")); // 400

		long chargeAmount = (testAmount > 0 ? testAmount : plan.getPrice().getAmount()); // 테스트금액 우선

		// 빌링키 식별자(customer_uid). 정기결제 채널로 결제창을 열 때만 넘긴다.
		//
		// 이 값은 게이트웨이가 발급하는 것이 아니라 우리가 정하는 이름이고, 게이트웨이가 실제 빌링키를
		// 여기에 1:1 로 묶어 자기 쪽에 보관한다. 그래서 사용자당 하나로 고정한다 — 재구독하면 같은
		// 자리에 새 빌링키가 덮이므로, 결제할 때마다 결제수단 행이 늘어나던 문제가 구조적으로 사라진다.
		//
		// 여기서는 결제수단을 만들지 않는다. 예전에는 이 자리에서 "temp_" + 현재시각으로 자리표를 만들어
		// 등록했는데, 결제창이 열리기도 전이라 성공 여부와 무관하게 매번 한 행씩 쌓였고(사용자 1번에 41행),
		// 무엇보다 빌링키가 묶이지 않은 값이라 자동 청구가 그걸 customer_uid 로 보내 전부 거절당했다.
		// 등록은 결제가 확정되고 빌링키 발급이 확인된 뒤에 markSucceededAndProvision 에서 한다.
		String customerUid = mappedPg.startsWith("kakaopay.TCSUBSCRIP") ? billingCustomerUid(userId) : null;

		// 결제수단은 확정 단계에서 연결한다(위 주석 참고). 여기서는 비워 둔다.
		PaymentMethod paymentMethod = null;

		// 먼저 게이트웨이에서 세션 생성
		User user = User.reference(userId);
		
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
		payment.attachPaymentMethod(paymentMethod); // 결제수단 연결
		paymentRepository.save(payment); // 저장
		// 멱등키는 위에서 이미 선삽입했다(처리 전에 선점해야 동시 요청을 판정할 수 있다).

		PaymentCheckoutCreateSuccessResponseDto res = new PaymentCheckoutCreateSuccessResponseDto(); // 응답 DTO
		res.redirectUrl = session.redirectUrl; // prepare-only 전환 이후 null (프론트 SDK가 결제창 호출)
		res.providerSessionId = session.sessionId; // merchant_uid(세션)
		res.amount = chargeAmount; // 결제 금액(검증용)
		res.paymentId = payment.getId(); // 내부 결제 ID
		res.pg = mappedPg; // 프론트 PG 코드 전달
		res.customerUid = customerUid; // 빌링키 식별자(정기결제 채널일 때만)
		return res; // 반환
	}

	/**
	 * 사용자의 빌링키 식별자 — 게이트웨이가 실제 빌링키를 이 이름에 묶어 보관한다.
	 *
	 * 사용자당 하나로 고정한다. 시각이나 난수를 섞으면 결제할 때마다 새 이름이 생기고, 이전 이름에
	 * 묶인 빌링키는 아무도 참조하지 않는 채로 게이트웨이에 남는다. 고정해 두면 재구독이 같은 자리를
	 * 덮으므로 저장 결제수단도 사용자당 한 행으로 수렴한다.
	 */
	private String billingCustomerUid(Long userId) {
		return "ott_billing_" + userId;
	}

	/**
	 * 빌링키가 발급됐으면 저장 결제수단으로 등록하고 결제에 연결한다.
	 *
	 * 발급 여부는 우리 DB 로 알 수 없다. customer_uid 는 우리가 지은 이름일 뿐이고 실제 키는 게이트웨이가
	 * 들고 있으므로, 물어보는 것 말고는 확인할 방법이 없다. 그래서 여기서 한 번 조회한다.
	 *
	 * 미발급이면 아무것도 하지 않는다. 결제수단이 없으면 정기결제 배치가 청구 계획을 세우지 못해
	 * 연체로만 표시하고 넘어가는데, 그게 빌링키 없는 값으로 매 주기 거절당하며 재시도를 소진하는 것보다 낫다.
	 *
	 * @return 등록·연결된 결제수단, 빌링키가 없으면 null
	 */
	private PaymentMethod registerBillingKeyIfIssued(Payment payment) {
		Long userId = payment.getUser().getId();
		String customerUid = billingCustomerUid(userId);
		if (!paymentGateway.hasBillingKey(customerUid)) {
			log.info("빌링키 미발급 - 저장 결제수단을 등록하지 않는다. userId: {}, paymentId: {}", userId, payment.getId());
			return null;
		}

		// customer_uid 는 사용자당 고정이라 재구독해도 같은 행을 다시 쓴다.
		PaymentMethod existing = paymentMethodRepository
				.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(userId).stream()
				.filter(pm -> customerUid.equals(pm.getProviderMethodId()))
				.findFirst()
				.orElse(null);
		if (existing != null) {
			payment.attachPaymentMethod(existing);
			return existing;
		}

		PaymentMethod pm = PaymentMethod.createPaymentMethod(
				User.reference(userId), PaymentProvider.IMPORT, PaymentMethodType.KAKAO_PAY, customerUid);
		pm.applyListingOptions(true, 100, "카카오페이 정기결제");
		paymentMethodRepository.save(pm);
		payment.attachPaymentMethod(pm);
		log.info("빌링키 확인 - 저장 결제수단 등록 완료. userId: {}, customer_uid: {}", userId, customerUid);
		return pm;
	}

	/**
	 * 웹훅 반영(멱등)
	 * - 자기 트랜잭션 안에서 실행된다(processWebhook 은 트랜잭션을 열지 않는다).
	 *   여기 도달하기 전에 PG 선재검증이 끝나 있으므로, 이 트랜잭션 안에는 외부 호출이 없다.
	 */
	@Transactional
	public void applyWebhookEvent(Long paymentId, PaymentWebhookEventDto event) { // 웹훅 반영
		if (paymentId == null || event == null) { // 유효성 검사
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."); // 400
		}
		if (event.eventId != null && !event.eventId.isBlank() // 이벤트 멱등키
				&& idempotencyKeyRepository.findByKeyValue(event.eventId).isPresent()) { // 중복 확인
			return; // 이미 처리됨
		}
		// 락을 잡고 읽는다. eventId 멱등키는 "같은 웹훅의 재전송"만 막을 뿐,
		// 웹훅과 클라 확정처럼 서로 다른 경로가 같은 결제를 동시에 확정하는 것은 막지 못한다.
		// 그 판정은 이 락과 markSucceededAndProvision 의 상태 재확인이 한다.
		Payment payment = paymentRepository.findByIdForUpdate(paymentId) // 결제 단건 조회(비관적 쓰기 락)
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

		// 멱등키 선삽입: 위쪽 findByKeyValue 는 빠른 경로일 뿐 경합을 못 막는다(확인과 저장 사이가 벌어짐).
		// 처리 시작 전에 먼저 넣어 유니크 제약이 동시 웹훅을 판정하게 한다.
		// saveAndFlush 로 INSERT 를 여기서 확정시켜, 제약 위반을 이 자리에서 잡아 전용 예외로 좁힌다.
		// (트랜잭션은 어차피 롤백되지만, 뒤쪽 처리에서 나는 다른 제약 위반과 구분되어야 한다 —
		//  컨트롤러가 200 으로 흡수하는 것은 이 예외뿐이다.)
		if (event.eventId != null && !event.eventId.isBlank()) { // 이벤트 멱등 저장
			try {
				idempotencyKeyRepository.saveAndFlush(IdempotencyKey.createIdempotencyKey(
						event.eventId, // 키
						"payment.webhook", // 용도
						null, // 응답
						LocalDateTime.now() // 적재 시각
				));
			} catch (DataIntegrityViolationException e) { // 동시 웹훅 경합에서 진 쪽
				throw new DuplicateWebhookEventException(event.eventId, e);
			}
		}

		if (event.status == PaymentStatus.SUCCEEDED) { // 성공
			// 공통 확정 로직으로 수렴(웹훅·클라이언트 확정·대사 배치가 동일 경로 사용, 멱등)
			markSucceededAndProvision(payment, event.providerPaymentId, event.receiptUrl, ts);

		} else if (event.status == PaymentStatus.FAILED) { // 실패
			payment.applyGatewayFailure(ts); // 상태 + 실패 시각
			// 구독 전이: 활성 구독이 있으면 PAST_DUE로 표시(즉시 경고 상태), 재시도는 배치가 수행
			subscriptionRepository.findActiveEffectiveByUser(payment.getUser().getId(), MembershipSubscriptionStatus.ACTIVE, ts)
					.ifPresent(sub -> sub.applyPaymentFailure(ts)); // 연체 전환 + 최근 실패 시각

		} else if (event.status == PaymentStatus.CANCELED) { // 취소
			payment.applyGatewayCancellation(ts); // 상태 + 취소 시각
			// 구독 전이: 자동갱신 중단 + 말일 해지 예약
			subscriptionRepository.findActiveEffectiveByUser(payment.getUser().getId(), MembershipSubscriptionStatus.ACTIVE, ts)
					.ifPresent(sub -> sub.scheduleCancellationAtPeriodEnd()); // 자동갱신 중단 + 말일 해지 예약

		} else if (event.status == PaymentStatus.REFUNDED) { // 환불
			payment.applyGatewayRefund(event.amount, ts); // 상태 + 환불 시각 + 금액
			// 구독 전이: 환불 시 즉시 해지 처리(정책)
			subscriptionRepository.findActiveEffectiveByUser(payment.getUser().getId(), MembershipSubscriptionStatus.ACTIVE, ts)
					.ifPresent(sub -> sub.applyImmediateCancellation(ts)); // 즉시 해지 + 해지 시각 + 자동갱신 중단

		} else { // 방어
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 이벤트 상태입니다."); // 400
		}
	}

	/**
	 * 환불 정책 검증 후 환불 실행
	 * - 조건: 결제일로부터 7일 이내 AND 전혀 시청하지 않음
	 *
	 * 3단계로 쪼갠 이유 (completePayment 와 같은 형태)
	 * - 예전에는 이 메서드 전체가 클래스 레벨 @Transactional 안에서 돌았다. 락 없이 findById 로 읽은 상태로
	 *   "SUCCEEDED 인가"를 판정하고 곧바로 issueRefund 를 불렀으므로, 동시 요청 둘이 모두 가드를 통과하면
	 *   환불 API 가 두 번 나간다. 실제로 이중 환불까지 가는지는 아임포트가 두 번째를 거절해 주느냐에 달려
	 *   있어 미확인이지만, 남의 판정에 기대는 구조라는 것 자체가 결함이다.
	 * - 게다가 그 PG 호출이 트랜잭션 안에 있었다(5절 위반). DB 쓰기와 외부 결제 호출이 한 트랜잭션이었다.
	 * - 그래서 락만 추가하는 것은 답이 아니다. 그러면 아임포트 응답 시간 내내 payments 행 락을 쥐게 되는데
	 *   9절이 "락 구간에 외부 API 호출이 들어가면 비관적 락을 쓰지 않는다"고 한다. 분리가 락의 전제다.
	 *
	 * 왜 클라이언트 멱등키가 아니라 결정적 키인가
	 * - 클라이언트가 만든 키는 같은 문자열일 때만 충돌한다. 탭이 둘이거나 클릭마다 새 UUID 를 만드는
	 *   클라이언트는 서로 다른 키를 들고 오므로 둘 다 통과해 환불 API 가 그대로 두 번 나간다.
	 *   막아야 할 대상이 "같은 요청의 재전송"이 아니라 "같은 결제에 대한 두 번째 환불"이기 때문이다.
	 * - 그래서 키를 paymentId 에서 결정적으로 파생한다(6절: 결제 재시도 키는 주문 식별자에서 파생한다).
	 *   클라이언트가 무엇을 보내든 결제 1건당 키는 하나뿐이라 유니크 제약이 항상 판정에 걸린다.
	 *   API 계약은 그대로다 — 클라이언트가 새로 보낼 것이 없다.
	 * - 판정을 락이 아니라 유니크 제약에 맡기는 이유가 여기 있다. 락은 3단계의 DB 쓰기만 직렬화할 뿐,
	 *   그 앞에서 이미 나가버린 issueRefund 두 번을 되돌리지 못한다. 돈이 나가는 호출을 한 번으로 묶으려면
	 *   호출 전에 선점이 끝나 있어야 한다(5절: 외부 호출 전에 그 시도를 DB 에 남긴다).
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED) // 환불 호출이 트랜잭션 안에 들어가지 않게 한다
	public void refundIfEligible(Long userId, Long paymentId) { // 환불 엔드포인트 진입점
		RefundTarget target = self.claimRefund(userId, paymentId); // 1단계: 정책 검증 + 환불권 선점(커밋됨)
		try {
			// 2단계: 트랜잭션 밖에서 환불 호출(전액 환불). 여기서 실패해도 1단계의 선점은 남는다 — 5절이
			// 요구하는 "기록 없는 호출 금지"의 대가다. 타임아웃은 실패가 아니므로 자동 재시도하지 않는다.
			PaymentGateway.RefundResult rr = paymentGateway.issueRefund(target.providerPaymentId(), target.amount());
			// 3단계: 락 + 상태 재확인 + 확정
			self.confirmRefunded(paymentId, target.amount(), rr.refundedAt != null ? rr.refundedAt : LocalDateTime.now());
		} catch (ResponseStatusException e) {
			throw e; // 이미 상태/사유가 있는 예외는 그대로 전파(markSucceededAndProvision 과 같은 처리)
		} catch (Exception e) {
			log.error("환불 실패 - paymentId: {}, imp_uid: {}", paymentId, target.providerPaymentId(), e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "환불 처리 중 오류가 발생했습니다: " + e.getMessage());
		}
	}

	/**
	 * 환불 1단계 — 정책을 검증하고 이 결제의 환불권을 선점한다.
	 * - 여기서 보는 상태는 락 없는 빠른 경로다. 중복을 실제로 판정하는 것은 아래 멱등키 선삽입이고,
	 *   3단계가 락을 잡고 한 번 더 확인한다.
	 * @return 환불 대상(트랜잭션 밖에서 환불 API 를 부르는 데 필요한 값)
	 */
	@Transactional
	public RefundTarget claimRefund(Long userId, Long paymentId) {
		Payment payment = paymentRepository.findById(paymentId) // 결제 단건 조회(락 없는 빠른 경로)
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
		// 시청 조건: 시청 이력이 없어야 함 (시청 이력이 있으면 환불 불가)
		int totalWatched = playerProgressReadService.sumWatchedSecondsSincePaidEpisodes(userId, payment.getPaidAt()); // 4화 이상 누적 시청 초 합
		if (totalWatched > 0) { // 시청 이력이 있으면 환불 불가
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "콘텐츠를 시청한 경우 환불이 불가합니다."); // 400
		}
		// 환불권 선점: 정책 검증을 모두 통과한 뒤에 잡는다. 순서가 중요하다 — 정책으로 거절당한 요청까지
		// 키를 태우면 그 결제는 나중의 정당한 환불도 영구히 막힌다(키는 결제당 하나뿐이고 반납 수단이 없다).
		String refundKey = refundIdempotencyKey(paymentId);
		if (idempotencyKeyRepository.findByKeyValue(refundKey).isPresent()) { // 빠른 경로: 이미 선점된 환불
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리 중이거나 처리된 환불 요청입니다."); // 409
		}
		try {
			// saveAndFlush 로 INSERT 를 여기서 확정시켜, 제약 위반을 이 자리에서 잡는다(checkout 과 같은 형태).
			// CLAIMED 로 넣는다: 이 키는 게이트웨이 호출 전에 커밋되므로 아직 환불이 나갔는지 모른다.
			// 호출이 예외로 끝나 확정되지 못한 키는 대사 배치가 역조회해 풀거나 확정한다.
			idempotencyKeyRepository.saveAndFlush(IdempotencyKey.createClaimedIdempotencyKey(
					refundKey, // 키(paymentId 파생)
					REFUND_KEY_PURPOSE, // 용도
					LocalDateTime.now() // 선점 시각
			));
		} catch (DataIntegrityViolationException e) { // 동시 요청 경합에서 진 쪽
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리 중이거나 처리된 환불 요청입니다.", e); // 409(빠른 경로와 같은 응답)
		}
		return new RefundTarget(payment.getProviderPaymentId(), payment.getPrice().getAmount());
	}

	/**
	 * 환불 3단계 — 결제 행을 잠그고 상태를 다시 확인한 뒤 환불을 확정한다.
	 * - 이 트랜잭션 안에는 외부 호출이 없다. 환불 API 는 호출자가 트랜잭션 밖에서 끝내고 들어온다(9절).
	 *
	 * 이 락이 무엇을 막고 무엇을 막지 않는지
	 * - 환불 API 경로의 중복은 이 락이 아니라 1단계의 결정적 멱등키가 막는다. 락은 PG 호출 뒤에 걸리므로
	 *   이미 나간 호출을 되돌리지 못한다. 여기 락의 몫은 확정 순서를 잡는 것뿐이다.
	 * - 남는 경쟁 상대는 REFUNDED 웹훅(applyWebhookEvent)이다. 그쪽은 환불 멱등키를 지나지 않고
	 *   같은 행에 같은 전이를 쓰므로, 두 경로를 직렬화하는 것은 payments 행 락뿐이다.
	 * - 다만 웹훅 경로는 상태 가드 없이 덮어쓰기 때문에 락이 있으나 없으나 최종 값이 같다. 그래서
	 *   이 락은 RefundIdempotencyTest 로 검증되지 않는다(제거해도 테스트가 통과한다). 정본 확정 형태와의
	 *   일관성, 그리고 위 "이미 REFUNDED 면 return" 가드가 의미를 갖게 하려고 남긴다.
	 */
	@Transactional
	public void confirmRefunded(Long paymentId, long refundedAmount, LocalDateTime refundedAt) {
		Payment payment = paymentRepository.findByIdForUpdate(paymentId) // 락을 잡고 최신 상태로 다시 읽는다
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제가 존재하지 않습니다.")); // 400
		// ⚠ 아래 "이미 REFUNDED 면 return" 가드보다 앞에서 전이해야 한다. 뒤에 두면 REFUNDED 웹훅이 먼저
		// 도착한 결제의 키가 영원히 CLAIMED 로 남아 대사 배치가 매번 게이트웨이를 역조회한다.
		idempotencyKeyRepository.findByKeyValue(refundIdempotencyKey(paymentId))
				.ifPresent(IdempotencyKey::markConfirmed);
		if (payment.getStatus() == PaymentStatus.REFUNDED) { // 이미 반영됨(멱등, 락 아래에서 재확인된 상태)
			return;
		}
		// 전액 환불: 상태 + 환불 금액 + 환불 시각을 함께 확정
		payment.applyGatewayRefund(refundedAmount, refundedAt);
		paymentRepository.save(payment); // 저장

		// 환불 시 멤버십 구독 즉시 해지
		Long ownerId = payment.getUser().getId();
		LocalDateTime now = LocalDateTime.now();
		subscriptionRepository.findActiveEffectiveByUser(ownerId, MembershipSubscriptionStatus.ACTIVE, now)
				.ifPresent(sub -> {
					sub.applyImmediateCancellation(now); // 즉시 해지 + 해지 시각 + 자동갱신 중단
					subscriptionRepository.save(sub);
					log.info("환불로 인한 멤버십 구독 해지 - userId: {}, subscriptionId: {}", ownerId, sub.getId());
				});

		log.info("환불 성공 - paymentId: {}, imp_uid: {}", payment.getId(), payment.getProviderPaymentId());
	}

	/**
	 * 환불 대상 — 트랜잭션 밖에서 환불 API 를 부르기 위해 결제에서 뽑아둔 값들.
	 * - 트랜잭션이 닫힌 뒤에 쓰이므로 엔티티를 그대로 들고 나가지 않는다(지연 로딩 필드 접근 방지).
	 */
	public record RefundTarget(String providerPaymentId, long amount) {}

	/**
	 * 환불 멱등키 — 결제 1건당 정확히 하나.
	 * - 클라이언트 입력이 아니라 paymentId 에서 결정적으로 파생한다. 재시도할 때마다 새 UUID 를 만들면
	 *   충돌하지 않아 아무것도 막지 못한다(6절).
	 */
	private static String refundIdempotencyKey(Long paymentId) {
		return REFUND_KEY_PURPOSE + ":" + paymentId;
	}

	/** 환불 멱등키의 용도 값. 대사 배치가 이 값으로 대상을 좁힌다. */
	public static final String REFUND_KEY_PURPOSE = "payment.refund";

	/**
	 * 환불 선점 대사 — 확정되지 못한 선점 1건을 게이트웨이에 역조회해 해제하거나 확정한다.
	 *
	 * 왜 이 경로가 필요한가
	 * - 1단계의 선점은 커밋되고 2단계의 게이트웨이 호출은 트랜잭션 밖이라, 호출이 예외로 끝나면
	 *   키만 남는다. 키에는 TTL 도 반납 경로도 없어 그 결제는 API 로 다시 환불할 수 없었다.
	 * - 그렇다고 실패를 곧바로 "환불 안 나감"으로 볼 수는 없다. 타임아웃은 나갔을 수도 있어서,
	 *   풀어주면 이중 환불이 된다. 그래서 푸는 판단을 게이트웨이 역조회에 맡긴다.
	 *
	 * @return 선점을 정리했으면 true(해제 또는 확정), 판정 불가로 그대로 두었으면 false
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED) // 아래 역조회가 외부 호출이라 트랜잭션 밖에 둔다(9절)
	public boolean reconcileRefundClaim(String refundKeyValue) {
		Long paymentId = paymentIdFromRefundKey(refundKeyValue);
		if (paymentId == null) {
			log.warn("환불 선점 대사 건너뜀 - 키 형식 불일치: {}", refundKeyValue);
			return false;
		}
		RefundTarget target = self.loadRefundTarget(paymentId); // 1단계: 역조회에 필요한 값만 뽑는다
		if (target == null || target.providerPaymentId() == null) {
			log.warn("환불 선점 대사 건너뜀 - 역조회할 imp_uid 없음, paymentId: {}", paymentId);
			return false;
		}
		// 2단계: 트랜잭션 밖에서 환불 여부 역조회
		PaymentGateway.RefundStatus status = paymentGateway.findRefundStatus(target.providerPaymentId());
		switch (status) {
			case NOT_REFUNDED -> {
				// 환불이 나가지 않았음이 확인됐다 → 선점을 지워 재환불을 열어준다.
				// 상태로 남기지 않고 행을 지우는 이유: key_value 유니크 제약과 claimRefund 의 빠른 경로가
				// 상태를 보지 않으므로, 행이 남아 있으면 어떤 상태값이든 재환불이 그대로 막힌다.
				self.releaseRefundClaim(refundKeyValue);
				log.info("환불 선점 해제 - paymentId: {}, imp_uid: {}", paymentId, target.providerPaymentId());
				return true;
			}
			case REFUNDED -> {
				// 환불은 실제로 나갔는데 확정만 못 했다 → 정상 3단계와 같은 후처리로 수렴시킨다.
				self.confirmRefunded(paymentId, target.amount(), LocalDateTime.now());
				log.info("환불 선점 확정 - paymentId: {}, imp_uid: {}", paymentId, target.providerPaymentId());
				return true;
			}
			default -> {
				log.warn("환불 선점 판정 불가 - 선점 유지, paymentId: {}, imp_uid: {}", paymentId, target.providerPaymentId());
				return false;
			}
		}
	}

	/**
	 * 환불 선점 대사 1단계 — 역조회에 필요한 값만 뽑는다(트랜잭션 밖에서 쓰이므로 엔티티를 들고 나가지 않는다).
	 * @return 결제가 없으면 null
	 */
	@Transactional(readOnly = true)
	public RefundTarget loadRefundTarget(Long paymentId) {
		return paymentRepository.findById(paymentId)
				.map(p -> new RefundTarget(p.getProviderPaymentId(), p.getPrice().getAmount()))
				.orElse(null);
	}

	/**
	 * 환불 선점 해제 — 멱등키 행을 지운다.
	 * - CLAIMED 인 동안에만 지운다. 역조회와 이 트랜잭션 사이에 웹훅이나 다른 경로가 확정을 끝냈다면
	 *   그 키는 확정된 환불의 키이므로 지우면 안 된다.
	 */
	@Transactional
	public void releaseRefundClaim(String refundKeyValue) {
		idempotencyKeyRepository.findByKeyValue(refundKeyValue)
				.filter(k -> k.getStatus() == IdempotencyKeyStatus.CLAIMED)
				.ifPresent(idempotencyKeyRepository::delete);
	}

	/**
	 * 환불 멱등키에서 결제 식별자를 되뽑는다.
	 * @return 형식이 맞지 않으면 null
	 */
	private static Long paymentIdFromRefundKey(String refundKeyValue) {
		String prefix = REFUND_KEY_PURPOSE + ":";
		if (refundKeyValue == null || !refundKeyValue.startsWith(prefix)) {
			return null;
		}
		try {
			return Long.valueOf(refundKeyValue.substring(prefix.length()));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * 결제 성공 확정 + 멤버십 지급 (공통 확정 로직)
	 * - 웹훅 / 클라이언트 확정 / 대사 배치가 모두 이 메서드로 수렴한다.
	 * - 멱등: 이미 SUCCEEDED면 아무 것도 하지 않아 중복 지급을 방지한다.
	 *
	 * ⚠ 호출자는 반드시 payments 행을 잠근 채로 들어와야 한다(findByIdForUpdate).
	 * - 아래 가드는 "조회 후 분기"라, 잠그지 않고 읽은 상태로 판정하면 동시 요청 둘 다 통과한다.
	 *   락이 없으면 이 메서드는 멱등하지 않다. 락을 잡는 자리는 confirmSucceeded 와 applyWebhookEvent 다.
	 */
	private void markSucceededAndProvision(Payment payment, String providerPaymentId, String receiptUrl, LocalDateTime paidAt) {
		if (payment.getStatus() == PaymentStatus.SUCCEEDED) { // 이미 확정됨(멱등, 락 아래에서 재확인된 상태)
			return; // 재지급 방지
		}
		// 상태·결제시각·완료시각·외부 결제 ID 를 한 번에 확정(영수증은 있을 때만 갱신 — 클라 경로는 null일 수 있음)
		payment.applyGatewaySuccess(providerPaymentId, receiptUrl, paidAt);
		paymentRepository.save(payment); // 저장
		log.info("결제 SUCCEEDED 확정 - paymentId: {}, imp_uid: {}", payment.getId(), providerPaymentId);

		// PG 응답으로 결제수단 type/brand 최종 확정 (아임포트 pay_method와 1:1 매핑)
		try {
			PaymentGateway.PaymentDetails details = paymentGateway.fetchPaymentDetails(providerPaymentId);
			PaymentMethod pm = payment.getPaymentMethod();
			if (pm == null) {
				// 체크아웃은 결제수단을 만들지 않는다. 결제가 확정된 지금에야 빌링키가 실제로 묶였는지
				// 게이트웨이에 물어볼 수 있고, 확인된 경우에만 저장 결제수단으로 승격시킨다.
				// 확인 안 되면 등록하지 않는다 — 자동 청구가 쓸 수 없는 값을 결제수단으로 남기지 않으려는 것이다.
				pm = registerBillingKeyIfIssued(payment);
			}
			if (pm != null) {
				String payMethod = details.payMethod == null ? "" : details.payMethod.trim().toLowerCase();
				PaymentMethodType type;
				String brand;
				switch (payMethod) { // pay_method와 1:1 매핑
					case "card":
						type = PaymentMethodType.CARD;
						String cardName = details.cardName;
						brand = cardName != null && !cardName.isBlank() ? cardName.trim().toUpperCase() : "CARD";
						break;
					case "kakaopay":
						type = PaymentMethodType.KAKAO_PAY;
						brand = null; // 간편결제는 brand 불필요
						break;
					case "tosspayments":
					case "toss":
						type = PaymentMethodType.TOSS_PAY;
						brand = null; // 간편결제는 brand 불필요
						break;
					case "nice":
						type = PaymentMethodType.NICE_PAY;
						brand = null; // 간편결제는 brand 불필요
						break;
					default:
						type = PaymentMethodType.CARD; // 기본값
						brand = "UNKNOWN";
				}
				pm.applyGatewayMethodDetails(PaymentProvider.IMPORT, type, brand);
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
					objectMapper.writeValueAsString(evt), // payload(JSON)
					LocalDateTime.now() // 적재 시각
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
	 *
	 * 3단계로 쪼갠 이유
	 * - 예전에는 이 메서드 전체가 한 트랜잭션이었고, 락 없이 읽은 상태로 멱등 가드를 통과한 뒤
	 *   PG 재검증을 부르고 확정했다. 가드를 통과한 시점과 확정하는 시점 사이가 PortOne 응답 시간만큼
	 *   벌어져 있어서, 그 사이에 웹훅이 먼저 확정을 끝내도 이 경로는 자기가 아까 내린 결론대로 또 지급했다.
	 *   결제 1건에 구독 2건·아웃박스 2건이 생기고 영수증 메일이 2통 나갔다.
	 * - 그렇다고 처음부터 락을 잡으면 PG 응답을 기다리는 내내 payments 행 락을 쥐게 된다(9절이 금지).
	 *   그래서 재검증은 트랜잭션 밖에서 끝내고, 확정만 짧은 트랜잭션 안에서 락을 잡고 한다.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED) // PG 재검증이 트랜잭션 안에 들어가지 않게 한다
	public void completePayment(Long userId, Long paymentId, String impUid) {
		CompletionTarget target = self.prepareCompletion(userId, paymentId); // 1단계: 소유자 검증 + 빠른 가드
		if (target == null) { // 이미 확정됨(멱등)
			return;
		}
		if (impUid == null || impUid.isBlank()) { // imp_uid 필수
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imp_uid가 필요합니다."); // 400
		}
		// 2단계: 트랜잭션 밖에서 PG 재검증(클라 응답 자체는 신뢰하지 않음)
		boolean valid = paymentGateway.verifyPayment(impUid, target.providerSessionId(), target.expectedAmount());
		if (!valid) { // 재검증 실패
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제 검증에 실패했습니다. (PG 재검증 불일치)"); // 400
		}
		self.confirmSucceeded(paymentId, impUid, null, LocalDateTime.now()); // 3단계: 락 + 상태 재확인 + 지급
	}

	/**
	 * 클라이언트 확정 1단계 — 소유자를 확인하고 재검증에 필요한 값을 뽑는다.
	 * - 여기서 보는 상태는 빠른 경로일 뿐이다. 실제 중복 판정은 3단계가 락을 잡고 다시 한다.
	 * @return 확정 대상, 이미 확정돼 할 일이 없으면 null
	 */
	@Transactional(readOnly = true)
	public CompletionTarget prepareCompletion(Long userId, Long paymentId) {
		Payment payment = paymentRepository.findById(paymentId) // 결제 단건 조회(락 없는 빠른 경로)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "결제가 존재하지 않습니다.")); // 404
		if (!payment.getUser().getId().equals(userId)) { // 소유자 검증
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 결제만 확정할 수 있습니다."); // 403
		}
		if (payment.getStatus() == PaymentStatus.SUCCEEDED) { // 이미 확정됨(멱등)
			return null;
		}
		return new CompletionTarget(
				payment.getProviderSessionId(), // merchant_uid(재검증 대조용)
				payment.getPrice() != null ? payment.getPrice().getAmount() : 0L); // 기대 금액(서버 확정, 테스트 1원)
	}

	/**
	 * 확정 단계 — 결제 행을 잠그고 상태를 다시 확인한 뒤 지급한다.
	 * - 클라 확정과 대사 배치가 공유하는 마지막 단계다(웹훅은 applyWebhookEvent 가 이미 락을 잡고 들어온다).
	 * - 이 트랜잭션 안에는 외부 호출이 없다. PG 재검증은 호출자가 트랜잭션 밖에서 끝내고 들어온다(9절).
	 */
	@Transactional
	public void confirmSucceeded(Long paymentId, String providerPaymentId, String receiptUrl, LocalDateTime paidAt) {
		Payment payment = paymentRepository.findByIdForUpdate(paymentId) // 락을 잡고 최신 상태로 다시 읽는다
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제가 존재하지 않습니다.")); // 400
		markSucceededAndProvision(payment, providerPaymentId, receiptUrl, paidAt); // 공통 확정 로직으로 수렴
	}

	/**
	 * 클라이언트 확정 대상 — 트랜잭션 밖에서 PG 재검증을 하기 위해 결제에서 뽑아둔 값들.
	 * - 트랜잭션이 닫힌 뒤에 쓰이므로 엔티티를 그대로 들고 나가지 않는다(지연 로딩 필드 접근 방지).
	 */
	public record CompletionTarget(String providerSessionId, long expectedAmount) {}

	/**
	 * 대사(reconciliation) — 오래된 미확정(PENDING) 결제를 아임포트 실제 상태로 정리
	 * - 이중 확인(클라 확정/웹훅)이 모두 실패한 희귀 케이스까지 복구하는 최후 방어선.
	 * - PENDING은 imp_uid가 없으므로 merchant_uid로 역조회한다.
	 * - paid면 확정/지급(공통 로직 수렴), failed/cancelled면 상태 전이, 아직 미결이면 건너뜀.
	 * @return 상태가 확정적으로 정리되면 true
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED) // 아임포트 역조회가 트랜잭션 안에 들어가지 않게 한다
	public boolean reconcilePending(Long paymentId) {
		String merchantUid = self.prepareReconcile(paymentId); // 1단계: 대사 대상 여부 판정
		if (merchantUid == null) {
			return false; // 대상 아님(이미 확정/취소됨, 차액 결제)
		}
		PaymentGateway.ReconcileResult r =
				paymentGateway.findPaymentBySessionId(merchantUid); // 2단계: 트랜잭션 밖에서 세션 식별자 역조회
		if (!r.found || r.status == null) {
			return false; // 결제 시도 기록 없음(prepare만) → 유지
		}
		return self.applyReconcileResult(paymentId, r, LocalDateTime.now()); // 3단계: 락 + 상태 재확인 + 반영
	}

	/**
	 * 대사 1단계 — 대사 대상인지 판정하고 역조회 키만 뽑는다.
	 * - 여기서 본 PENDING 은 빠른 경로일 뿐이다. 실제 판정은 3단계가 락을 잡고 다시 한다.
	 * @return 역조회할 merchant_uid, 대상이 아니면 null
	 */
	@Transactional(readOnly = true)
	public String prepareReconcile(Long paymentId) {
		Payment payment = paymentRepository.findById(paymentId).orElse(null); // 결제 조회(락 없는 빠른 경로)
		if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
			return null; // 대상 아님(이미 확정/취소됨)
		}
		// 차액(proration) 결제는 자체 complete 경로가 플랜 변경을 처리한다.
		// 여기서 확정하면 markSucceededAndProvision이 '새 구독'을 만들어 오처리되므로 건너뛴다.
		if (payment.getProviderSessionId() != null && payment.getProviderSessionId().startsWith("proration_")) {
			return null;
		}
		return payment.getProviderSessionId();
	}

	/**
	 * 대사 3단계 — 결제 행을 잠그고 상태를 다시 확인한 뒤 아임포트 실제 상태를 반영한다.
	 * - 1단계에서 본 PENDING 은 낡았을 수 있다. 아임포트에 물어보는 동안 클라 확정이나 웹훅이
	 *   먼저 확정을 끝냈다면 여기서 물러나야 한다. 그러지 않으면 같은 결제로 구독이 하나 더 생긴다.
	 */
	@Transactional
	public boolean applyReconcileResult(Long paymentId, PaymentGateway.ReconcileResult r, LocalDateTime now) {
		Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElse(null); // 락을 잡고 최신 상태로 다시 읽는다
		if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
			return false; // 대사 중에 다른 확정 경로가 먼저 정리함
		}
		// 정기결제 재청구는 전용 확정 경로가 처리한다.
		// 아래 markSucceededAndProvision 은 체크아웃 전제라 subscribe()로 '새 구독'을 만든다.
		// 재청구 대상 구독은 연장돼야 하므로 그대로 태우면 고아 구독이 생기고 원래 구독은 해지된다.
		if (RebillMerchantUid.isRebill(payment.getProviderSessionId())) {
			return recurringBillingService.reconcileRebillPayment(payment, r, now);
		}
		switch (r.status) {
			case PAID:
				long expected = (payment.getPrice() != null ? payment.getPrice().getAmount() : 0L); // 서버 확정 금액(테스트 1원)
				if (r.amount != expected) {
					log.warn("대사 금액 불일치 - paymentId: {}, expected: {}, actual: {}", paymentId, expected, r.amount);
					return false; // 금액 불일치는 자동 확정하지 않음(수동 확인 대상)
				}
				markSucceededAndProvision(payment, r.providerPaymentId, r.receiptUrl, now); // 공통 확정 로직으로 수렴
				log.info("대사 배치로 결제 확정 - paymentId: {}", paymentId);
				return true;
			case FAILED:
				payment.applyGatewayFailure(now);
				paymentRepository.save(payment);
				return true;
			case CANCELLED:
				payment.applyGatewayCancellation(now);
				paymentRepository.save(payment);
				return true;
			default:
				return false; // READY/UNKNOWN → 판정 불가, 미결 유지
		}
	}

}
