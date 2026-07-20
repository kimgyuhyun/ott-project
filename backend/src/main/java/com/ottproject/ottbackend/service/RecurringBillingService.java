package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.PaymentMethod;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.enums.PlanChangeType;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.PaymentMethodRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import com.ottproject.ottbackend.mybatis.MembershipSubscriptionQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
 * 메서드 개요
 * - runRecurringBilling: 정기결제 배치(스케줄)
 * - retryBilling: MQ 지연 메시지 도착 시 해당 구독 한 건만 재청구
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
	 */
	@Transactional // 청구/연장 원자성 보장
	public void retryBilling(Long subscriptionId, int attempt) {
		MembershipSubscription sub = subscriptionRepository.findById(subscriptionId).orElse(null); // 구독 조회
		if (sub == null) { // 삭제된 구독
			log.warn("재시도 대상 구독 없음 - subscriptionId: {}", subscriptionId);
			return;
		}
		if (sub.getStatus() != MembershipSubscriptionStatus.PAST_DUE || !sub.isAutoRenew()) { // 이미 복구/해지됨
			log.info("재시도 불필요(상태 변경됨) - subscriptionId: {}, status: {}", subscriptionId, sub.getStatus());
			return;
		}
		if (sub.getRetryCount() != attempt) { // 다른 경로(스윕)가 먼저 재시도한 스테일 메시지
			log.info("스테일 재시도 메시지 skip - subscriptionId: {}, messageAttempt: {}, currentRetryCount: {}",
					subscriptionId, attempt, sub.getRetryCount());
			return;
		}
		log.info("MQ 재시도 청구 시작 - subscriptionId: {}, attempt: {}", subscriptionId, attempt);
		billSubscription(sub, LocalDateTime.now()); // 청구 시도 + 성공/실패 처리(공통 로직)
	}

	/**
	 * 구독 1건 청구 + 성공/실패 처리 (스윕 배치·MQ 재시도 공통 경로)
	 * - 성공: 기간/next_billing_at 연장, 재시도 카운트 리셋
	 * - 실패: PAST_DUE 전환 후 MQ 지연 큐에 건별 재시도 예약(1차 3h, 2차 24h),
	 *         3회 소진 시 해지+메일. 발행 실패 시 기존 스윕 방식(+1일)으로 폴백.
	 */
	private void billSubscription(MembershipSubscription sub, LocalDateTime now) {
		List<PaymentMethod> methods = paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(sub.getUser().getId()); // 기본 우선 결제수단 목록(삭제 제외, 폴백 순회)
		if (methods.isEmpty()) { // 결제수단 없음
			sub.setStatus(MembershipSubscriptionStatus.PAST_DUE); // 연체 전환
			return; // 다음 구독 처리
		}

		long amount = sub.getMembershipPlan().getPrice().getAmount(); // 청구 금액(월 기준)
		String currency = "KRW"; // 통화 코드

		boolean charged = false; // 성공 여부 플래그
		String lastErrorCode = null; // 마지막 오류 코드
		String lastErrorMessage = null; // 마지막 오류 메시지

		for (PaymentMethod method : methods) { // 기본→보조 순차 시도
			try { // 청구 시도
				PaymentGateway.ChargeResult cr = paymentGateway.chargeWithSavedMethod(
					sub.getUser().getId().toString(), // 고객 식별자(예시: 내부 ID 사용)
					method.getProviderMethodId(), // 저장 결제수단 ID
					amount, // 청구 금액
					currency, // 통화
					"Subscription renewal" // 설명
				);

				Payment payment = Payment.createSucceededPayment( // 결제 레코드 생성
					sub.getUser(), // 사용자 FK
					sub.getMembershipPlan(), // 플랜 FK
					PaymentProvider.IMPORT, // 제공자(예시)
					cr.providerPaymentId, // 외부 결제 ID
					new com.ottproject.ottbackend.entity.Money(amount, currency), // 금액/통화 VO
					cr.paidAt // 결제 시각
				);
				payment.setReceiptUrl(cr.receiptUrl); // 영수증 URL 설정
				paymentRepository.save(payment); // 결제 저장

				LocalDateTime start = sub.getEndAt() != null && sub.getEndAt().isAfter(now) ? sub.getEndAt() : now; // 연장 시작점 계산
				LocalDateTime newEnd = start.plusMonths(sub.getMembershipPlan().getPeriodMonths()); // 새 종료 시각 계산
				sub.setEndAt(newEnd); // 종료 시각 갱신
				sub.setNextBillingAt(newEnd); // 다음 청구 시각 갱신
				sub.setStatus(MembershipSubscriptionStatus.ACTIVE); // 상태 유지/복구
				sub.setRetryCount(0); // 재시도 카운트 리셋
				sub.setLastRetryAt(now); // 마지막 시도 시각 기록
				sub.setLastErrorCode(null); // 에러 정보 초기화
				sub.setLastErrorMessage(null); // 에러 정보 초기화

				charged = true; // 성공 표시
				break; // 수단 루프 종료
			} catch (PaymentGateway.ChargeException ex) { // 게이트웨이 예외(유형 포함)
				lastErrorCode = ex.errorCode; // 코드 기록
				lastErrorMessage = ex.getMessage(); // 메시지 기록
				continue; // 다음 수단 시도
			} catch (Exception ex) { // 기타 예외는 소프트로 간주
				lastErrorCode = "UNKNOWN";
				lastErrorMessage = ex.getMessage();
				continue;
			}
		}

		if (!charged) { // 모든 수단 실패
			sub.setStatus(MembershipSubscriptionStatus.PAST_DUE); // 연체 전환
			sub.setLastRetryAt(now); // 마지막 시도 시각
			sub.setLastErrorCode(lastErrorCode); // 오류 코드 기록
			sub.setLastErrorMessage(lastErrorMessage); // 오류 메시지 기록

			int nextRetry = sub.getRetryCount() + 1; // 다음 재시도 횟수
			sub.setRetryCount(nextRetry); // 횟수 반영
			sub.setMaxRetry(3); // 정책 고정(3회)

			if (nextRetry >= 3) { // 최대 재시도 소진
				sub.setAutoRenew(false); // 자동갱신 중단
				sub.setCancelAtPeriodEnd(true); // 말일 해지 예약
				sub.setStatus(MembershipSubscriptionStatus.CANCELED); // 비활성화 처리(개발단계에서는 즉시 전환)
				sub.setCanceledAt(now); // 해지 확정 시각 기록
				// 알림: 결제 실패 누적 해지 안내 메일 발송
				notificationService.sendCanceledDueToDunning(sub.getUser(), sub);
			} else {
				// MQ 지연 큐에 건별 재시도 예약(1차: 3h, 2차: 24h 뒤 정확히 이 구독만 도착)
				boolean scheduled = billingRetryPublisher.scheduleRetry(sub.getId(), nextRetry);
				// 안전망: 메시지 유실 대비 스윕이 +3일 후 잡도록 예약(성공 시 nextBillingAt이 갱신돼 중복 없음).
				// 발행 실패(브로커 장애) 시에는 기존 스윕 방식(+1일)으로 폴백한다.
				sub.setNextBillingAt(scheduled ? now.plusDays(3) : now.plusDays(1));
			}
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
				// 플랜 변경 적용
				subscription.setMembershipPlan(subscription.getNextPlan());
				subscription.setNextPlan(null);
				subscription.setPlanChangeScheduledAt(null);
				subscription.setChangeType(null);
				
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
}


