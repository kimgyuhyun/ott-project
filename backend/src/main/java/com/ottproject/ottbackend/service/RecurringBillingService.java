package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.PaymentMethod;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.PaymentMethodRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import com.ottproject.ottbackend.mybatis.MembershipSubscriptionQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * - 최대 재시도 횟수 소진 시 자동 해지 및 알림 메일을 발송한다.
 *
 * 메서드 개요
 * - runRecurringBilling: 정기결제 배치(스케줄)
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

	/**
	 * 정기결제 배치
	 * - 6시간마다 실행, 실제 서비스에서는 주기 조정 필요
	 */
	@Scheduled(cron = "0 0 */6 * * *") // 6시간마다 실행
	@Transactional // 청구/연장 원자성 보장
	public void runRecurringBilling() { // 배치 진입점
		LocalDateTime now = LocalDateTime.now(); // 현재 시각
		log.info("정기결제 배치 시작 - {}", now);
		
		// MyBatis mapper를 사용하여 대상 구독만 효율적으로 조회
		List<MembershipSubscription> targetSubscriptions = membershipSubscriptionQueryMapper
			.findSubscriptionsForBilling(
				List.of(MembershipSubscriptionStatus.ACTIVE.name(), MembershipSubscriptionStatus.PAST_DUE.name()), 
				now
			);
		
		log.info("처리 대상 구독 수: {}", targetSubscriptions.size());
		
		for (MembershipSubscription sub : targetSubscriptions) { // 구독 순회
			// 기존 로직 유지 (필터링 제거 - 이미 매퍼에서 필터링됨)
			List<PaymentMethod> methods = paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(sub.getUser().getId()); // 기본 우선 결제수단 목록(삭제 제외, 폴백 순회)
			if (methods.isEmpty()) { // 결제수단 없음
				sub.setStatus(MembershipSubscriptionStatus.PAST_DUE); // 연체 전환
				continue; // 다음 구독 처리
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

					Payment payment = Payment.builder() // 결제 레코드 생성
						.user(sub.getUser()) // 사용자 FK
						.membershipPlan(sub.getMembershipPlan()) // 플랜 FK
						.provider(PaymentProvider.IMPORT) // 제공자(예시)
						.price(new com.ottproject.ottbackend.entity.Money(amount, currency)) // 금액/통화 VO
						.status(PaymentStatus.SUCCEEDED) // 성공 상태
						.providerPaymentId(cr.providerPaymentId) // 외부 결제 ID
						.receiptUrl(cr.receiptUrl) // 영수증 URL
						.paidAt(cr.paidAt) // 결제 시각
						.build();
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
					sub.setNextBillingAt(now.plusDays(1)); // 다음 시도 예약(+1일)
				}
			}
		}
		
		log.info("정기결제 배치 완료 - 처리된 구독: {}", targetSubscriptions.size());
	}
}


