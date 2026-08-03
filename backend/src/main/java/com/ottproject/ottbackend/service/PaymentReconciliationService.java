package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.IdempotencyKey;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.enums.IdempotencyKeyStatus;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.repository.IdempotencyKeyRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PaymentReconciliationService
 *
 * 큰 흐름
 * - 현업 표준 결제 확정 구조의 "최후 방어선"(reconciliation).
 * - 클라이언트 확정(동기)·웹훅(비동기)이 모두 실패해 PENDING으로 남은 결제를
 *   주기적으로 아임포트 실제 상태와 대사(對査)하여 확정/실패로 정리한다.
 *
 * 메서드 개요
 * - reconcilePendingPayments: 오래된 PENDING 결제 대사 배치(스케줄)
 * - reconcileStaleRefundClaims: 확정되지 못한 환불 선점 대사 배치(스케줄)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {
	private final PaymentRepository paymentRepository; // 결제 조회/저장
	private final PaymentCommandService paymentCommandService; // 건별 대사(멱등 확정) 위임
	private final IdempotencyKeyRepository idempotencyKeyRepository; // 환불 선점 키 조회

	/**
	 * 결제 대사 배치
	 * - 10분마다 실행. 생성 후 5분~24시간 사이의 PENDING 결제만 대상으로 한다.
	 *   (5분 미만: 아직 정상 확정 중일 수 있어 제외 / 24시간 초과: 사실상 미완료 시도라 제외)
	 * - 건별로 별도 트랜잭션에서 정리(PaymentCommandService.reconcilePending 프록시 호출).
	 */
	@Scheduled(cron = "0 */10 * * * *") // 10분마다 실행
	// 다중 인스턴스 중복 대사 방지(외부 결제 API 조회 + 상태 변경을 유발한다).
	@SchedulerLock(name = "PaymentReconciliationService_reconcilePendingPayments", lockAtMostFor = "PT9M", lockAtLeastFor = "PT30S")
	public void reconcilePendingPayments() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime from = now.minusHours(24); // 하한: 24시간 전
		LocalDateTime to = now.minusMinutes(5); // 상한: 5분 전

		List<Payment> targets = paymentRepository.findByStatusAndCreatedAtBetween(PaymentStatus.PENDING, from, to);
		if (targets.isEmpty()) {
			return; // 대상 없음
		}
		log.info("결제 대사 배치 시작 - 대상: {}건", targets.size());

		int resolved = 0;
		for (Payment p : targets) {
			try {
				if (paymentCommandService.reconcilePending(p.getId())) {
					resolved++;
				}
			} catch (Exception e) {
				log.warn("결제 대사 실패 - paymentId: {}", p.getId(), e);
			}
		}
		log.info("결제 대사 배치 완료 - 정리 {}/{}건", resolved, targets.size());
	}

	/**
	 * 환불 선점 대사 배치
	 * - 환불은 게이트웨이 호출 전에 멱등키를 커밋하므로, 그 호출이 예외로 끝나면 확정되지 못한 선점이 남는다.
	 *   키에는 TTL 도 반납 경로도 없어 그 결제는 API 로 다시 환불할 수 없다.
	 * - 10분 이상 지난 CLAIMED 선점만 대상으로 한다(진행 중인 정상 환불을 건드리지 않기 위한 유예).
	 *   IdempotencyKey 에는 updatedAt 이 없어 경과 시간 기준은 createdAt 뿐이다.
	 * - 실제 환불 여부 판정은 건별로 게이트웨이 역조회에 맡긴다. 판정 불가면 선점을 그대로 둔다 —
	 *   나갔을지 모르는 환불의 선점을 푸는 것은 이중 환불이다.
	 */
	@Scheduled(cron = "0 5/10 * * * *") // 10분마다(PENDING 대사와 시각을 어긋나게 둬 외부 API 호출이 겹치지 않게 한다)
	// 인스턴스가 2개라 락이 없으면 중복 실행되고, 건건이 외부 결제 API 를 부른다.
	// lockAtMostFor 는 cron 주기(10분)보다 짧아야 한다 — 주기를 넘기면 다음 실행이 통째로 스킵된다.
	@SchedulerLock(name = "PaymentReconciliationService_reconcileStaleRefundClaims", lockAtMostFor = "PT9M", lockAtLeastFor = "PT30S")
	public void reconcileStaleRefundClaims() {
		LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(10);

		List<IdempotencyKey> targets = idempotencyKeyRepository.findByPurposeAndStatusAndCreatedAtBefore(
				PaymentCommandService.REFUND_KEY_PURPOSE, IdempotencyKeyStatus.CLAIMED, staleBefore);
		if (targets.isEmpty()) {
			return; // 대상 없음
		}
		log.info("환불 선점 대사 배치 시작 - 대상: {}건", targets.size());

		int resolved = 0;
		for (IdempotencyKey key : targets) {
			try {
				if (paymentCommandService.reconcileRefundClaim(key.getKeyValue())) {
					resolved++;
				}
			} catch (Exception e) {
				log.warn("환불 선점 대사 실패 - key: {}", key.getKeyValue(), e);
			}
		}
		log.info("환불 선점 대사 배치 완료 - 정리 {}/{}건", resolved, targets.size());
	}
}
