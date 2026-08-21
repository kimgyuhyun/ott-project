package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.mybatis.PaymentQueryMapper;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.PaymentMethodRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * BillingAttemptRecorder
 *
 * 큰 흐름
 * - 재청구 시도를 PG 호출 "전에" PENDING Payment 로 남긴다. 하나의 행이 두 가지 역할을 한다.
 *   1) 중복 차단: 같은 시도는 같은 merchant_uid 라서 유니크 제약이 두 번째 삽입을 떨어뜨린다.
 *      이게 이중 청구를 막는 1차 방어선이다(비관적 락은 우리 DB 안의 동시성만 막는다).
 *   2) 회수 지점: 호출이 타임아웃 나거나 프로세스가 중간에 죽어도 PENDING 이 남아,
 *      PaymentReconciliationService 가 merchant_uid 로 아임포트에 역조회해 확정/실패로 정리한다.
 *      이전에는 청구 성공 후에야 Payment 를 만들어서, 실패하면 대사가 주울 대상 자체가 없었다.
 *
 * REQUIRES_NEW 인 이유
 * - 바깥 트랜잭션에 묶이면 롤백 시 흔적이 사라져 위 두 역할 다 무의미해진다. 먼저 커밋돼야 한다.
 * - 바깥이 구독 행 락을 쥐고 있어도 데드락 위험은 없다. 여기서는 payments 신규 삽입만 하고
 *   바깥이 잠근 subscriptions 행을 건드리지 않아 락 순환이 생길 수 없다.
 *
 * 메서드 개요
 * - openAttempt: 청구 시도 시작 기록(PENDING 삽입, 중복이면 예외)
 * - markAttemptFailed: 확정 실패로 닫기
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingAttemptRecorder {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentQueryMapper paymentQueryMapper; // 선점된 merchant_uid 의 정체 확인용 조회

    /**
     * 청구 시도 시작 기록
     * - 같은 merchant_uid 가 이미 있으면 DataIntegrityViolationException 이 난다. 호출자는 이걸
     *   "중복 배달"로 해석하고 청구를 중단해야 한다.
     * @return 생성된 PENDING 결제 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long openAttempt(Long userId, Long planId, Long paymentMethodId, String merchantUid, Money price) {
        Payment payment = Payment.createPendingPayment(
                userRepository.getReferenceById(userId), // FK 바인딩만 필요하므로 프록시로 충분
                membershipPlanRepository.getReferenceById(planId),
                PaymentProvider.IMPORT,
                merchantUid,
                price);
        payment.attachPaymentMethod(paymentMethodRepository.getReferenceById(paymentMethodId));
        payment.describeAs("Subscription renewal");
        paymentRepository.saveAndFlush(payment); // 제약 위반을 커밋까지 미루지 않고 여기서 드러낸다
        return payment.getId();
    }

    /**
     * 선점된 merchant_uid 가 "이미 거절된 시도"인지 확인
     *
     * - openAttempt 가 중복으로 막혔을 때, 두 경우를 구분해야 한다.
     *   1) PENDING/SUCCEEDED: 다른 배달이 지금 처리 중이거나 이미 청구됐다 → 중단해야 한다.
     *   2) FAILED: 직전 실행이 이 수단으로 시도했다 거절당한 뒤 끊긴 것이다(롤링 배포 중 재배달 등).
     *      → 다음 결제수단으로 이어가야 한다. 여기서 같이 중단하면 폴백이 영원히 1번 수단에서 막혀
     *      스윕이 몇 번을 돌아도 같은 지점에서 멈추고 구독이 고착된다.
     *
     * - 별도 트랜잭션인 이유: 삽입이 제약 위반으로 실패하면 그 트랜잭션은 이미 abort 상태라
     *   같은 트랜잭션에서 이어서 조회하면 "current transaction is aborted" 가 난다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean isAlreadyDeclined(String merchantUid) {
        Payment existing = paymentQueryMapper.findByProviderSessionId(merchantUid);
        return existing != null && existing.getStatus() == PaymentStatus.FAILED;
    }

    /**
     * 확정 실패로 닫기
     * - 아임포트가 명시적으로 거절한 경우에만 호출한다. 승인 여부가 불명이면 PENDING 으로 남겨
     *   대사 배치가 판단하게 해야 한다.
     * - 여기서 못 닫고 죽어도 PENDING 인 채로 대사가 같은 결론(failed)을 내므로 수렴한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAttemptFailed(Long paymentId, LocalDateTime failedAt) {
        paymentRepository.findById(paymentId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.markAsFailed(failedAt);
                paymentRepository.save(payment);
            }
        });
    }
}
