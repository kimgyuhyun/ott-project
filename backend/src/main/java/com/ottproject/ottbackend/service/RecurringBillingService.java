package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.PaymentMethod;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.PaymentMethodRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import com.ottproject.ottbackend.mybatis.MembershipSubscriptionQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RecurringBillingService
 *
 * 큰 흐름
 * - 자동갱신 구독에 대해 nextBillingAt 도래 시 저장 결제수단으로 자동 청구를 시도한다(기본→보조 폴백).
 * - 성공 시 구독 기간/nextBillingAt 갱신, 실패 시 PAST_DUE 전환 및 재시도/해지 처리 정책을 수행한다.
 * - 실패 재시도는 RabbitMQ 지연 큐(TTL+DLX)로 건별 예약(1차 3h, 2차 24h)하고,
 *   스윕 배치는 메시지 유실 대비 안전망(nextBillingAt +3일)으로만 동작한다.
 * - 최대 재시도 횟수 소진 시 자동 해지 및 알림 메일을 발송한다.
 *
 * 이중 청구 방어 구조
 * - 모든 청구는 PG 호출 "전에" 결정적 merchant_uid(RebillMerchantUid)로 PENDING Payment 를 먼저 커밋한다.
 *   같은 시도는 같은 uid 이므로 payments 의 유니크 제약이 중복 배달의 두 번째 청구를 삽입 단계에서 떨어뜨린다.
 *   이게 1차 방어선이고, 비관적 락은 그 위에서 상태 전이를 직렬화하는 보조 수단이다.
 * - 그 PENDING 행은 동시에 회수 지점이기도 하다. 응답을 못 받거나 프로세스가 죽어도 행이 남아
 *   PaymentReconciliationService 가 아임포트에 역조회해 확정/실패로 정리한다.
 *
 * 메서드 개요
 * - runRecurringBilling: 정기결제 배치(스케줄)
 * - retryBilling: MQ 지연 메시지 도착 시 해당 구독 한 건만 재청구
 * - reconcileRebillPayment: 대사 배치가 찾아낸 재청구 PENDING 결제 확정
 */
@Slf4j // 로깅 추가
@Service // 서비스 빈 등록
@RequiredArgsConstructor // 생성자 주입
public class RecurringBillingService { // 정기결제 스케줄러 서비스
	private final MembershipSubscriptionRepository subscriptionRepository; // 구독 리포지토리
	private final PaymentMethodRepository paymentMethodRepository; // 결제수단 리포지토리
	private final PaymentRepository paymentRepository; // 결제 리포지토리
	private final PaymentGateway paymentGateway; // 결제 게이트웨이 추상화
    private final MembershipNotificationService notificationService; // 알림 메일 서비스
    private final MembershipSubscriptionQueryMapper membershipSubscriptionQueryMapper; // MyBatis 구독 조회 매퍼
    private final BillingRetryPublisher billingRetryPublisher; // 재시도 지연 메시지 발행(RabbitMQ)
    private final BillingAttemptRecorder attemptRecorder; // 청구 시도 선기록(REQUIRES_NEW)

	// 단계별 트랜잭션을 프록시에 태우기 위한 자기 참조.
	// retryBilling 은 "락+가드 / PG 호출 / 결과 확정"을 서로 다른 트랜잭션으로 쪼개야 하는데,
	// 같은 빈 안에서 그냥 호출하면 프록시를 안 타서 @Transactional 이 무시된다.
	@Autowired
	@Lazy
	private RecurringBillingService self;

	/**
	 * 정기결제 배치
	 * - 6시간마다 실행, 실제 서비스에서는 주기 조정 필요
	 */
	@Scheduled(cron = "0 0 */6 * * *") // 6시간마다 실행
	// 다중 인스턴스 중복 청구 방지. cron 은 벽시계 정렬이라 인스턴스들이 정각에 동시 발화한다.
	// lockAtMostFor 는 넉넉히 잡는다 — 구독을 순회하며 외부 결제 API 를 건건이 호출하므로
	// 실행이 길다. 이 값보다 오래 걸리면 락이 먼저 풀려 다른 인스턴스가 중복 실행한다.
	@SchedulerLock(name = "RecurringBillingService_runRecurringBilling", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
	@Transactional // 청구/연장 원자성 보장
	public void runRecurringBilling() { // 배치 진입점
		LocalDateTime now = LocalDateTime.now(); // 현재 시각
		log.info("정기결제 배치 시작 - {}", now);

		// 1. 플랜 변경 예약된 구독 처리
		processScheduledPlanChanges(now);

		// 2. MyBatis mapper를 사용하여 대상 구독만 효율적으로 조회
		List<MembershipSubscription> targetSubscriptions = membershipSubscriptionQueryMapper
			.findSubscriptionsForBilling(
				List.of(MembershipSubscriptionStatus.ACTIVE.name(), MembershipSubscriptionStatus.PAST_DUE.name()),
				now
			);

		log.info("처리 대상 구독 수: {}", targetSubscriptions.size());

		for (MembershipSubscription sub : targetSubscriptions) { // 구독 순회
			billSubscription(sub, now); // 청구 시도 + 성공/실패 처리(공통 로직)
		}

		log.info("정기결제 배치 완료 - 처리된 구독: {}", targetSubscriptions.size());
	}

	/**
	 * MQ 지연 메시지 기반 건별 재시도 진입점
	 * - 대기 큐 TTL 만료 후 도착한 메시지로 해당 구독만 재청구한다(전체 폴링 없음).
	 * - 소비 시점 정합성 가드: 이미 복구/해지됐거나 스윕이 먼저 처리한 스테일 메시지는 건너뛴다.
	 *
	 * 3단계로 쪼갠 이유
	 * - 예전에는 구독 행을 잠근 채로 외부 결제 API 를 호출해서 락 보유 시간이 PG 응답 시간에 묶여 있었다.
	 *   이제 결정적 merchant_uid + 유니크 제약이 중복 청구를 막으므로, 락을 쥔 채 PG 를 부를 이유가 없다.
	 * - 1단계(락+가드)와 3단계(결과 확정)만 트랜잭션이고, 2단계 PG 호출은 락도 트랜잭션도 밖이다.
	 *   그래서 이 메서드 자체에는 @Transactional 을 걸지 않는다.
	 */
	public void retryBilling(Long subscriptionId, int attempt) {
		ChargePlan plan;
		try {
			plan = self.prepareRetry(subscriptionId, attempt); // 1단계: 락 + 가드 (짧은 트랜잭션)
		} catch (PessimisticLockingFailureException e) { // NOWAIT: 다른 트랜잭션이 같은 구독을 처리 중
			// 실패가 아니라 중복/경합이다. 기다렸어도 가드에 걸려 버려질 메시지이므로 조용히 종료하고,
			// 혹시 정상 건이었다면 스윕 배치가 다음 주기에 복구한다. ERROR 로 올리면 오탐 알림이 된다.
			log.info("재시도 메시지 경합 skip(다른 트랜잭션이 처리 중) - subscriptionId: {}, attempt: {}", subscriptionId, attempt);
			return;
		}
		if (plan == null) { // 가드에 걸렸거나 결제수단이 없음
			return;
		}

		log.info("MQ 재시도 청구 시작 - subscriptionId: {}, attempt: {}", subscriptionId, attempt);
		ChargeOutcome outcome = attemptCharge(plan); // 2단계: 락·트랜잭션 밖에서 PG 호출

		try {
			self.finishRetry(subscriptionId, outcome); // 3단계: 결과 확정 (짧은 트랜잭션)
		} catch (PessimisticLockingFailureException e) {
			// 이미 돈은 나갔지만 결제는 PENDING 으로 남아 있다. 대사 배치가 아임포트에 확인하고
			// reconcileRebillPayment 로 확정/연장까지 마무리하므로 유실되지 않는다.
			log.warn("재청구 결과 확정 지연(락 경합) - subscriptionId: {}, 대사 배치가 정리한다", subscriptionId);
		}
	}

	/**
	 * 재시도 1단계 — 구독 행을 잠그고 가드를 통과시킨 뒤 청구 계획을 만든다.
	 * - 락은 여기서만 잡고 바로 놓는다. 중복 배달의 실제 차단은 merchant_uid 유니크 제약이 한다.
	 * @return 청구 계획, 청구하면 안 되는 상태면 null
	 */
	@Transactional
	public ChargePlan prepareRetry(Long subscriptionId, int attempt) {
		MembershipSubscription sub = subscriptionRepository.findByIdForUpdate(subscriptionId).orElse(null); // 구독 조회(비관적 쓰기 락)
		if (sub == null) { // 삭제된 구독
			log.warn("재시도 대상 구독 없음 - subscriptionId: {}", subscriptionId);
			return null;
		}
		if (sub.getStatus() != MembershipSubscriptionStatus.PAST_DUE || !sub.isAutoRenew()) { // 이미 복구/해지됨
			log.info("재시도 불필요(상태 변경됨) - subscriptionId: {}, status: {}", subscriptionId, sub.getStatus());
			return null;
		}
		if (sub.getRetryCount() != attempt) { // 다른 경로(스윕)가 먼저 재시도한 스테일 메시지
			log.info("스테일 재시도 메시지 skip - subscriptionId: {}, messageAttempt: {}, currentRetryCount: {}",
					subscriptionId, attempt, sub.getRetryCount());
			return null;
		}
		ChargePlan plan = planFor(sub);
		if (plan == null) { // 결제수단 없음
			sub.markPastDue(); // 연체 유지(시도가 없었으므로 카운트는 그대로)
		}
		return plan;
	}

	/**
	 * 재시도 3단계 — 청구 결과를 구독 상태에 반영한다.
	 * - 미확정/중복이면 구독 행을 읽을 필요조차 없다(상태를 건드리면 안 되는 케이스).
	 */
	@Transactional
	public void finishRetry(Long subscriptionId, ChargeOutcome outcome) {
		if (outcome.result() == ChargeOutcome.Result.AMBIGUOUS || outcome.result() == ChargeOutcome.Result.DUPLICATE) {
			return; // 아래 applyChargeOutcome 주석 참고
		}
		MembershipSubscription sub = subscriptionRepository.findByIdForUpdate(subscriptionId).orElse(null);
		if (sub == null) {
			log.warn("재청구 결과 반영 대상 구독 없음 - subscriptionId: {}", subscriptionId);
			return;
		}
		applyChargeOutcome(sub, outcome, LocalDateTime.now());
	}

	/**
	 * 구독 1건 청구 + 성공/실패 처리 (스윕 배치 경로)
	 * - 스윕은 배치 트랜잭션 안에서 순차 처리한다. 이 경로는 구독 행 락을 잡지 않으며,
	 *   재시도 경로와는 nextBillingAt(+3일)으로 시간 분리돼 있어 동시에 같은 구독을 청구하지 않는다.
	 */
	private void billSubscription(MembershipSubscription sub, LocalDateTime now) {
		ChargePlan plan = planFor(sub);
		if (plan == null) { // 결제수단 없음 / 주기 앵커 없음
			sub.markPastDue(); // 연체 전환(시도가 없었으므로 카운트는 그대로)
			return; // 다음 구독 처리
		}
		ChargeOutcome outcome = attemptCharge(plan);
		applyChargeOutcome(sub, outcome, now);
	}

	/**
	 * 청구 계획 수립 — 결제수단 목록과 merchant_uid 구성 요소를 모은다.
	 * - 구독 엔티티에서 필요한 값을 미리 뽑아둔다. 재시도 경로는 트랜잭션이 닫힌 뒤에 이 계획으로 청구하므로
	 *   지연 로딩 필드를 나중에 건드리면 안 된다.
	 * @return 청구 계획, 청구가 불가능하면 null
	 */
	private ChargePlan planFor(MembershipSubscription sub) {
		List<PaymentMethod> methods = paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(sub.getUser().getId()); // 기본 우선 결제수단 목록(삭제 제외, 폴백 순회)
		if (methods.isEmpty()) { // 결제수단 없음
			return null;
		}
		// 청구주기 앵커: endAt 이 정답이다(청구 성공 시에만 움직임). nextBillingAt 은 실패할 때마다
		// 갱신되므로 앵커로 못 쓰고, 여기서는 endAt 이 없는 무기한/레거시 행의 폴백으로만 쓴다.
		LocalDateTime cycleAnchor = sub.getEndAt() != null ? sub.getEndAt() : sub.getNextBillingAt();
		if (cycleAnchor == null) {
			log.warn("청구주기 앵커 없음(endAt/nextBillingAt 모두 null) - subscriptionId: {}", sub.getId());
			return null;
		}
		return new ChargePlan(
				sub.getId(),
				sub.getUser().getId(),
				sub.getMembershipPlan().getId(),
				sub.getMembershipPlan().getPrice().getAmount(), // 청구 금액(월 기준)
				cycleAnchor,
				sub.getRetryCount(), // 이번 시도 번호(재시도 경로는 가드가 메시지 attempt 와 일치를 보장)
				methods
		);
	}

	/**
	 * 결제수단을 순차로 시도한다(기본→보조 폴백).
	 * - 트랜잭션을 스스로 관리하지 않는다. 재시도 경로는 트랜잭션 밖에서, 스윕은 배치 트랜잭션 안에서 호출한다.
	 * - 각 시도는 PG 호출 전에 PENDING 을 먼저 커밋한다(BillingAttemptRecorder 주석 참고).
	 */
	private ChargeOutcome attemptCharge(ChargePlan plan) {
		String currency = "KRW"; // 통화 코드
		String lastErrorCode = null; // 마지막 오류 코드
		String lastErrorMessage = null; // 마지막 오류 메시지

		for (PaymentMethod method : plan.methods()) { // 기본→보조 순차 시도
			String merchantUid = RebillMerchantUid.create(plan.subscriptionId(), plan.cycleAnchor(), plan.attempt(), method.getId());

			Long paymentId;
			try {
				paymentId = attemptRecorder.openAttempt(plan.userId(), plan.planId(), method.getId(), merchantUid, new Money(plan.amount(), currency));
			} catch (DataIntegrityViolationException dup) {
				if (attemptRecorder.isAlreadyDeclined(merchantUid)) {
					// 직전 실행이 이 수단으로 시도했다 거절당한 뒤 끊긴 것이다(재배달/스윕 재진입).
					// 여기서 중단하면 폴백이 영원히 이 수단에서 막혀 구독이 고착된다.
					log.info("이미 거절된 결제수단 건너뜀 - subscriptionId: {}, merchantUid: {}", plan.subscriptionId(), merchantUid);
					lastErrorCode = "ALREADY_DECLINED";
					lastErrorMessage = "이전 실행에서 거절된 결제수단";
					continue;
				}
				// 같은 시도가 지금 진행 중이거나 이미 청구됐다 = 중복 배달. 멈추지 않으면 그게 이중 청구다.
				log.info("중복 청구 시도 차단(merchant_uid 선점됨) - subscriptionId: {}, merchantUid: {}", plan.subscriptionId(), merchantUid);
				return ChargeOutcome.duplicate();
			}

			try { // 청구 시도
				PaymentGateway.ChargeResult cr = paymentGateway.chargeWithSavedMethod(
					String.valueOf(plan.userId()), // 고객 식별자(예시: 내부 ID 사용)
					method.getProviderMethodId(), // 저장 결제수단 ID
					merchantUid, // 결정적 주문번호(중복 청구 시 게이트웨이도 거절)
					plan.amount(), // 청구 금액
					currency, // 통화
					"Subscription renewal" // 설명
				);
				return ChargeOutcome.charged(paymentId, cr);
			} catch (PaymentGateway.ChargeException ex) { // 게이트웨이 예외(유형 포함)
				if (ex.failureType == PaymentGateway.FailureType.AMBIGUOUS) {
					// 승인됐는지 알 수 없다. 실패로 처리해 다음 시도를 예약하면 새 merchant_uid 로 또 청구된다.
					// PENDING 을 남긴 채 중단하고, 대사 배치가 아임포트에 물어본 뒤 확정한다.
					log.warn("재청구 결과 미확정 - subscriptionId: {}, merchantUid: {}, 사유: {}",
							plan.subscriptionId(), merchantUid, ex.getMessage());
					return ChargeOutcome.ambiguous();
				}
				attemptRecorder.markAttemptFailed(paymentId, LocalDateTime.now()); // 확정 실패로 닫는다
				lastErrorCode = ex.errorCode; // 코드 기록
				lastErrorMessage = ex.getMessage(); // 메시지 기록
			} catch (Exception ex) { // 게이트웨이 계약 밖의 예외
				// 승인 여부를 알 수 없으므로 확정 실패로 취급하면 안 된다(위와 같은 이유).
				log.warn("재청구 결과 미확정(예상 못한 예외) - subscriptionId: {}, merchantUid: {}",
						plan.subscriptionId(), merchantUid, ex);
				return ChargeOutcome.ambiguous();
			}
		}

		return ChargeOutcome.declined(lastErrorCode, lastErrorMessage); // 모든 수단이 명시적으로 거절됨
	}

	/**
	 * 청구 결과를 구독 상태에 반영한다(호출자의 트랜잭션 안에서 수행).
	 * - 성공: 결제 확정 + 기간/next_billing_at 연장, 재시도 카운트 리셋
	 * - 확정 실패: PAST_DUE 전환 후 MQ 지연 큐에 건별 재시도 예약(1차 3h, 2차 24h),
	 *   3회 소진 시 해지+메일. 발행 실패 시 기존 스윕 방식(+1일)으로 폴백.
	 * - 미확정/중복: 아무것도 바꾸지 않는다. retryCount 를 올리면 다음 시도가 새 merchant_uid 로
	 *   또 청구돼 이중 청구가 되고, 상태를 바꾸면 대사가 확정할 때 기준이 흔들린다.
	 */
	private void applyChargeOutcome(MembershipSubscription sub, ChargeOutcome outcome, LocalDateTime now) {
		switch (outcome.result()) {
			case AMBIGUOUS, DUPLICATE -> {
				return;
			}
			case CHARGED -> {
				// 결제 확정과 구독 연장을 같은 트랜잭션에서 끝낸다. 중간에 죽으면 결제는 PENDING 으로 남고
				// 대사 배치가 reconcileRebillPayment 로 둘 다 마무리한다.
				paymentRepository.findById(outcome.paymentId()).ifPresent(payment -> {
					if (payment.getStatus() == PaymentStatus.PENDING) {
						payment.markAsSucceeded(outcome.providerPaymentId(), outcome.paidAt());
						payment.attachReceipt(outcome.receiptUrl());
						paymentRepository.save(payment);
					}
				});
				extendAfterSuccess(sub, now);
			}
			case DECLINED -> {
				// 연체 전환 + 실패 기록 + 재시도 카운트 증가를 한 번에(엔티티가 짝을 보장)
				int nextRetry = sub.recordDeclinedCharge(now, outcome.lastErrorCode(), outcome.lastErrorMessage());

				if (nextRetry >= 3) { // 최대 재시도 소진
					sub.cancelAfterDunningExhausted(now); // 해지 + 해지 시각 + 자동갱신 중단 + 말일 해지 예약
					// 알림: 결제 실패 누적 해지 안내 메일 발송
					notificationService.sendCanceledDueToDunning(sub.getUser(), sub);
				} else {
					// MQ 지연 큐에 건별 재시도 예약(1차: 3h, 2차: 24h 뒤 정확히 이 구독만 도착)
					boolean scheduled = billingRetryPublisher.scheduleRetry(sub.getId(), nextRetry);
					// 안전망: 메시지 유실 대비 스윕이 +3일 후 잡도록 예약(성공 시 nextBillingAt이 갱신돼 중복 없음).
					// 발행 실패(브로커 장애) 시에는 기존 스윕 방식(+1일)으로 폴백한다.
					sub.scheduleNextBillingAt(scheduled ? now.plusDays(3) : now.plusDays(1));
				}
			}
		}
	}

	/**
	 * 청구 성공 후 구독 연장/복구 (정상 경로와 대사 확정 경로 공통)
	 */
	private void extendAfterSuccess(MembershipSubscription sub, LocalDateTime now) {
		LocalDateTime start = sub.getEndAt() != null && sub.getEndAt().isAfter(now) ? sub.getEndAt() : now; // 연장 시작점 계산
		LocalDateTime newEnd = start.plusMonths(sub.getMembershipPlan().getPeriodMonths()); // 새 종료 시각 계산
		sub.renewUntil(newEnd, now); // 기간/청구일 갱신 + 상태 복구 + 던닝 기록 초기화
	}

	/**
	 * 대사 배치가 찾아낸 재청구 PENDING 결제 확정
	 *
	 * - 체크아웃 경로(PaymentCommandService.markSucceededAndProvision)를 재청구에 쓸 수 없다.
	 *   그 경로는 membershipCommandService.subscribe() 로 "새 구독"을 만든다. 재청구 대상 구독은
	 *   PAST_DUE 라 연장 조건에도 안 걸려서, 연장돼야 할 원래 구독은 그대로 던닝을 돌다 해지되고
	 *   그 옆에 고아 구독이 하나 더 생긴다(차액 결제를 대사에서 제외하는 것과 정확히 같은 이유).
	 * - paid: 결제 확정 + 원래 구독 연장/복구. 정상 재청구 성공과 같은 결과로 수렴한다.
	 * - failed/cancelled: 결제만 닫는다. 던닝 진행 여부는 재시도/스윕 경로가 판단한다.
	 *
	 * 구독 행은 비관적 락으로 읽는다. 경합하면 예외가 나면서 이 건 전체가 롤백되고(결제 확정도 함께),
	 * 10분 뒤 다음 대사 주기에 다시 시도한다 — 반쯤 반영된 상태로 커밋되는 것보다 낫다.
	 */
	@Transactional
	public boolean reconcileRebillPayment(Payment payment, PaymentGateway.ReconcileResult r, LocalDateTime now) {
		if (payment.getStatus() != PaymentStatus.PENDING) {
			return false; // 이미 정리됨
		}
		Long subscriptionId = RebillMerchantUid.subscriptionIdOf(payment.getProviderSessionId());

		switch (r.status) {
			case "paid":
				long expected = (payment.getPrice() != null ? payment.getPrice().getAmount() : 0L); // 서버 확정 금액
				if (r.amount != expected) {
					log.warn("재청구 대사 금액 불일치 - paymentId: {}, expected: {}, actual: {}", payment.getId(), expected, r.amount);
					return false; // 금액 불일치는 자동 확정하지 않음(수동 확인 대상)
				}
				payment.markAsSucceeded(r.providerPaymentId, now);
				if (r.receiptUrl != null) {
					payment.attachReceipt(r.receiptUrl);
				}
				paymentRepository.save(payment);
				if (subscriptionId == null) {
					log.error("재청구 대사 - merchant_uid 에서 구독 ID 추출 실패, 구독 연장 누락: {}", payment.getProviderSessionId());
					return true; // 결제는 확정됐으므로 대사 대상에서는 빠진다
				}
				subscriptionRepository.findByIdForUpdate(subscriptionId).ifPresent(sub -> extendAfterSuccess(sub, now));
				log.info("대사로 재청구 확정 - paymentId: {}, subscriptionId: {}", payment.getId(), subscriptionId);
				return true;
			case "failed":
				payment.markAsFailed(now);
				paymentRepository.save(payment);
				return true;
			case "cancelled":
			case "canceled":
				payment.applyGatewayCancellation(now);
				paymentRepository.save(payment);
				return true;
			default:
				return false; // ready 등 미결 상태 → 유지
		}
	}

	/**
	 * 플랜 변경 예약된 구독 처리
	 * - 다음 결제일이 도래한 플랜 변경 예약 구독들을 처리
	 */
	private void processScheduledPlanChanges(LocalDateTime now) {
		log.info("플랜 변경 예약 구독 처리 시작 - {}", now);

		// 플랜 변경 예약된 구독 조회
		List<MembershipSubscription> scheduledPlanChanges = membershipSubscriptionQueryMapper
				.findSubscriptionsWithScheduledPlanChanges(now);

		log.info("플랜 변경 예약 구독 수: {}", scheduledPlanChanges.size());

		for (MembershipSubscription subscription : scheduledPlanChanges) {
			try {
				// 플랜 변경 적용(교체와 예약 해제를 함께 — 예약이 남으면 다음 배치가 또 적용한다)
				subscription.changePlanTo(subscription.getNextPlan());

				// 구독 정보 저장
				subscriptionRepository.save(subscription);

				// 플랜 변경 완료 알림 발송
				notificationService.sendPlanChangeNotification(
						subscription.getUser(),
						subscription,
						subscription.getMembershipPlan()
				);

				log.info("플랜 변경 완료 - userId: {}, newPlan: {}",
						subscription.getUser().getId(),
						subscription.getMembershipPlan().getName());

			} catch (Exception e) {
				log.error("플랜 변경 처리 실패 - userId: {}, subscriptionId: {}",
						subscription.getUser().getId(),
						subscription.getId(), e);
			}
		}

		log.info("플랜 변경 예약 구독 처리 완료 - 처리된 구독: {}", scheduledPlanChanges.size());
	}

	/**
	 * 청구 계획 — 트랜잭션 밖에서 PG 를 호출하기 위해 구독에서 뽑아둔 값들.
	 */
	public record ChargePlan(
			Long subscriptionId,
			Long userId,
			Long planId,
			long amount,
			LocalDateTime cycleAnchor,
			int attempt,
			List<PaymentMethod> methods
	) {}

	/**
	 * 청구 결과
	 * - CHARGED: 승인 확인됨
	 * - DECLINED: 모든 결제수단이 명시적으로 거절됨(던닝 진행 대상)
	 * - AMBIGUOUS: 승인 여부 불명(던닝 진행 금지, 대사가 판단)
	 * - DUPLICATE: 같은 시도가 이미 기록됨(중복 배달, 아무것도 하지 않음)
	 */
	public record ChargeOutcome(
			Result result,
			Long paymentId,
			String providerPaymentId,
			LocalDateTime paidAt,
			String receiptUrl,
			String lastErrorCode,
			String lastErrorMessage
	) {
		public enum Result { CHARGED, DECLINED, AMBIGUOUS, DUPLICATE }

		static ChargeOutcome charged(Long paymentId, PaymentGateway.ChargeResult cr) {
			return new ChargeOutcome(Result.CHARGED, paymentId, cr.providerPaymentId, cr.paidAt, cr.receiptUrl, null, null);
		}

		static ChargeOutcome declined(String errorCode, String errorMessage) {
			return new ChargeOutcome(Result.DECLINED, null, null, null, null, errorCode, errorMessage);
		}

		static ChargeOutcome ambiguous() {
			return new ChargeOutcome(Result.AMBIGUOUS, null, null, null, null, null, null);
		}

		static ChargeOutcome duplicate() {
			return new ChargeOutcome(Result.DUPLICATE, null, null, null, null, null, null);
		}
	}
}
