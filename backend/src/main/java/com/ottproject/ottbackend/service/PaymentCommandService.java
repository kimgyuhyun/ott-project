package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.MembershipSubscribeRequestDto;
import com.ottproject.ottbackend.dto.PaymentCheckoutCreateRequestDto;
import com.ottproject.ottbackend.dto.PaymentCheckoutCreateSuccessResponseDto;
import com.ottproject.ottbackend.dto.PaymentWebhookEventDto;
import com.ottproject.ottbackend.entity.IdempotencyKey;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.repository.IdempotencyKeyRepository;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.mybatis.PaymentQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import com.ottproject.ottbackend.service.ImportPaymentGateway;

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
	private final MembershipCommandService membershipCommandService; // 멤버십 쓰기 서비스
	private final PaymentGateway paymentGateway; // 게이트웨이 어댑터(IMPORT 구현 주입)
	private final PlayerProgressReadService playerProgressReadService; // 플레이어 진행률 읽기 서비스(누적 시청 검증)
	private final MembershipSubscriptionRepository subscriptionRepository; // 구독 리포지토리(웹훅 전이 반영)
	private final PaymentQueryMapper paymentQueryMapper; // 결제 조회 매퍼

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
		
		// 4. 결제 이벤트 처리
		processPaymentEvent(event);
		
		// 5. API 재검증 (보안 강화)
		if (event.status == PaymentStatus.SUCCEEDED && event.providerPaymentId != null) {
			verifyWebhookWithApi(event);
		}
		
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
			if (event.amount == null || event.amount <= 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "성공 웹훅에 금액 누락");
			}
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
		
		// 상태별 처리
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
			com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper(); // ObjectMapper 생성
			return om.readValue(rawBody, com.ottproject.ottbackend.dto.PaymentWebhookEventDto.class); // JSON 파싱
		} catch (Exception e) {
			throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid webhook payload"); // 400
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
			mappedPg = "kakaopay.TC0ONETIME"; // 기본값: 카카오
		} else if ("kakao".equals(normalizedService)) {
			mappedPg = "kakaopay.TC0ONETIME";
		} else if ("toss".equals(normalizedService)) {
			mappedPg = "tosspayments";
		} else if ("nice".equals(normalizedService)) {
			mappedPg = "nice";
		} else {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 paymentService 입니다.");
		}
		if (req.idempotencyKey != null && !req.idempotencyKey.isBlank() // 멱등키 전달 시
				&& idempotencyKeyRepository.findByKeyValue(req.idempotencyKey).isPresent()) { // 중복 확인
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 요청입니다."); // 409
		}
		MembershipPlan plan = membershipPlanRepository.findByCode(req.planCode) // 플랜 조회
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "플랜이 존재하지 않습니다.")); // 400

		long chargeAmount = (testAmount > 0 ? testAmount : plan.getPrice().getAmount()); // 테스트금액 우선

		Payment payment = Payment.builder() // 결제 엔티티 생성
				.user(User.builder().id(userId).build()) // 사용자 FK
				.membershipPlan(plan) // 플랜 FK
				.provider(com.ottproject.ottbackend.enums.PaymentProvider.IMPORT) // IMPORT 사용
				.price(new Money(chargeAmount, plan.getPrice().getCurrency())) // Money VO 사용
				.status(PaymentStatus.PENDING) // 초기 상태
				.build(); // 빌드
		paymentRepository.save(payment); // 저장

		PaymentGateway.CheckoutSession session = paymentGateway.createCheckoutSession( // 게이트웨이 세션 생성 (prepare-only)
				payment.getUser(), // 사용자 정보
				plan, // 플랜 정보
				req.successUrl, // 성공 URL (웹훅/회계용 전달)
				req.cancelUrl, // 취소 URL (웹훅/회계용 전달)
				req.paymentService, // 선택 결제 서비스(프론트 SDK 매핑 용도만)
				chargeAmount // 실제 prepare 금액(테스트 시 1원 등)
		); // 세션 반환

		payment.setProviderSessionId(session.sessionId); // 세션 ID 저장
		paymentRepository.save(payment); // 업데이트 반영

		if (req.idempotencyKey != null && !req.idempotencyKey.isBlank()) { // 멱등키 저장
			idempotencyKeyRepository.save(IdempotencyKey.builder() // 멱등 엔티티 생성
					.keyValue(req.idempotencyKey) // 키
					.purpose("payment.checkout") // 용도
					.createdAt(LocalDateTime.now()) // 생성 시각
					.build()); // 빌드
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
			log.info("결제 성공 웹훅 처리 - paymentId: {}, imp_uid: {}", payment.getId(), event.providerPaymentId);
			
			payment.setStatus(PaymentStatus.SUCCEEDED); // 상태
			payment.setPaidAt(ts); // 시각
			payment.setProviderPaymentId(event.providerPaymentId); // 외부 ID (imp_uid)
			payment.setReceiptUrl(event.receiptUrl); // 영수증
			
			// 결제 정보 즉시 저장
			paymentRepository.save(payment);
			log.info("결제 정보 저장 완료 - imp_uid: {}", event.providerPaymentId);

			// 멤버십 구독 생성
			MembershipSubscribeRequestDto dto = new MembershipSubscribeRequestDto(); // 구독 DTO
			dto.planCode = payment.getMembershipPlan().getCode(); // 플랜 코드
			membershipCommandService.subscribe(payment.getUser().getId(), dto); // 구독 반영
			log.info("멤버십 구독 생성 완료 - userId: {}, planCode: {}", payment.getUser().getId(), dto.planCode);

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
			idempotencyKeyRepository.save(IdempotencyKey.builder() // 엔티티
					.keyValue(event.eventId) // 키
					.purpose("payment.webhook") // 용도
					.createdAt(LocalDateTime.now()) // 시각
					.build()); // 빌드
		}
	}

	/**
	 * 환불 정책 검증 후 환불 실행
	 * - 조건: 결제 24시간 이내 AND 누적 시청 < 300초
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
		// 시간 조건: 24시간 이내
		if (payment.getPaidAt().plusHours(24).isBefore(LocalDateTime.now())) { // 24시간 초과
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "환불 가능 기간을 초과했습니다."); // 400
		}
		// 누적 시청 < 5분(300초) 조건: 결제 시각 이후 positionSec 합산(4화 이상만)
		int totalWatched = playerProgressReadService.sumWatchedSecondsSincePaidEpisodes(userId, payment.getPaidAt()); // 4화 이상 누적 시청 초 합
		if (totalWatched >= 300) { // 300초 이상 시청
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시청 기록으로 환불 불가 정책입니다."); // 400
		}
		// 게이트웨이 환불 실행(전액 환불)
		PaymentGateway.RefundResult rr = paymentGateway.issueRefund(payment.getProviderPaymentId(), payment.getPrice().getAmount()); // 환불 호출
		payment.setStatus(PaymentStatus.REFUNDED); // 상태 전환
		payment.setRefundedAmount(payment.getPrice().getAmount()); // 전액 환불
		payment.setRefundedAt(rr.refundedAt != null ? rr.refundedAt : LocalDateTime.now()); // 환불 시각 기록
		paymentRepository.save(payment); // 저장
	}
	
	/**
	 * 결제 성공 시 즉시 처리
	 * - 결제 상태를 즉시 SUCCEEDED로 변경하고 DB에 저장
	 * - 멤버십 구독을 즉시 생성하여 상태 동기화 보장
	 */
	public void processPaymentSuccess(Long paymentId, String providerPaymentId, String receiptUrl) {
		Payment payment = paymentRepository.findById(paymentId) // 결제 단건 조회
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제가 존재하지 않습니다.")); // 400
		
		if (payment.getStatus() == PaymentStatus.SUCCEEDED) { // 이미 성공 상태인 경우
			return; // 중복 처리 방지
		}
		
		LocalDateTime now = LocalDateTime.now(); // 현재 시각
		
		// 결제 상태 즉시 업데이트
		payment.setStatus(PaymentStatus.SUCCEEDED); // 성공 상태로 변경
		payment.setPaidAt(now); // 결제 완료 시각 설정
		payment.setProviderPaymentId(providerPaymentId); // 아임포트 결제 ID 설정
		payment.setReceiptUrl(receiptUrl); // 영수증 URL 설정
		paymentRepository.save(payment); // DB에 즉시 저장
		
		// 멤버십 구독 즉시 생성
		createMembershipSubscription(payment.getUser().getId(), payment.getMembershipPlan().getCode());
	}
	
	/**
	 * 멤버십 구독 생성 로직
	 * - 결제 성공 시 즉시 구독을 생성하여 상태 동기화
	 */
	private void createMembershipSubscription(Long userId, String planCode) {
		try {
			MembershipSubscribeRequestDto dto = new MembershipSubscribeRequestDto(); // 구독 DTO 생성
			dto.planCode = planCode; // 플랜 코드 설정
			membershipCommandService.subscribe(userId, dto); // 구독 생성
		} catch (Exception e) {
			// 구독 생성 실패 시 로깅 (결제는 성공했으므로 롤백하지 않음)
			log.error("멤버십 구독 생성 실패 - userId: {}, planCode: {}", userId, planCode, e);
		}
	}

	/**
	 * 아임포트 API로 웹훅 데이터 재검증
	 * - 웹훅 처리 후 실제 결제 상태를 API로 확인하여 보안 강화
	 */
	private void verifyWebhookWithApi(PaymentWebhookEventDto event) {
		try {
			// ImportPaymentGateway로 캐스팅하여 API 재검증 메서드 호출
			if (paymentGateway instanceof ImportPaymentGateway) {
				ImportPaymentGateway importGateway = (ImportPaymentGateway) paymentGateway;
				
				// 결제 상태 재검증
				boolean isValid = importGateway.verifyPaymentStatus(
					event.providerPaymentId, 
					event.providerSessionId, 
					event.amount != null ? event.amount.longValue() : 0
				);
				
				if (!isValid) {
					log.error("API 재검증 실패 - imp_uid: {}, merchant_uid: {}", 
						event.providerPaymentId, event.providerSessionId);
					// 재검증 실패 시 로깅만 하고 웹훅 처리는 계속 진행
					// (이미 DB에 반영되었을 수 있으므로)
				} else {
					log.info("API 재검증 성공 - imp_uid: {}, merchant_uid: {}", 
						event.providerPaymentId, event.providerSessionId);
				}
			}
		} catch (Exception e) {
			log.error("API 재검증 중 예외 발생 - imp_uid: {}, merchant_uid: {}", 
				event.providerPaymentId, event.providerSessionId, e);
			// 재검증 실패 시 로깅만 하고 웹훅 처리는 계속 진행
		}
	}
}
