package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.admin.AdminSubscriptionDto;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AdminSubscriptionService
 *
 * 큰 흐름
 * - 운영자가 손으로 부르는 구독 보정/조회를 담당한다.
 *
 * 왜 이 클래스가 생겼는가
 * - 이전에는 AdminController 가 리포지토리를 직접 호출했고, 지연 로딩을 읽으려고
 *   컨트롤러에 @Transactional 을 달고 있었다. 트랜잭션 경계는 서비스에 있어야 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSubscriptionService {

    private final MembershipSubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;

    /**
     * 환불된 결제에 대응하는 활성 구독을 즉시 해지한다.
     * 이미 해지된 구독은 조회 조건(ACTIVE)에서 빠지므로 여러 번 실행해도 결과가 같다.
     *
     * @return 해지 처리한 구독 수
     */
    @Transactional
    public int cancelSubscriptionsForRefundedPayments() {
        List<Payment> refundedPayments = paymentRepository.findByStatus(PaymentStatus.REFUNDED);
        log.info("환불된 결제 수: {}", refundedPayments.size());

        LocalDateTime now = LocalDateTime.now();
        int fixedCount = 0;

        for (Payment payment : refundedPayments) {
            Long userId = payment.getUser().getId();

            Optional<MembershipSubscription> active = subscriptionRepository
                    .findActiveEffectiveByUser(userId, MembershipSubscriptionStatus.ACTIVE, now);
            if (active.isEmpty()) {
                continue;
            }

            MembershipSubscription subscription = active.get();
            subscription.applyImmediateCancellation(now); // 상태 + 해지 시각 + 자동갱신 중단을 한 번에
            subscriptionRepository.save(subscription);

            log.info("구독 해지 완료 - userId: {}, subscriptionId: {}, paymentId: {}",
                    userId, subscription.getId(), payment.getId());
            fixedCount++;
        }

        return fixedCount;
    }

    /**
     * 특정 사용자의 현재 유효 구독을 조회한다.
     * DTO 변환까지 이 트랜잭션 안에서 끝내므로 지연 로딩 연관(플랜)을 안전하게 읽는다.
     *
     * @param userId 사용자 ID
     * @return 유효 구독이 있으면 DTO, 없으면 빈 값
     */
    @Transactional(readOnly = true)
    public Optional<AdminSubscriptionDto> findActiveSubscription(Long userId) {
        return subscriptionRepository
                .findActiveEffectiveByUser(userId, MembershipSubscriptionStatus.ACTIVE, LocalDateTime.now())
                .map(AdminSubscriptionDto::from);
    }
}
