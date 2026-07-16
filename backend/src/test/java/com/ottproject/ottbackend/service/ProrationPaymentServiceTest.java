package com.ottproject.ottbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.OutboxEventRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProrationPaymentService 차액 계산 단위 테스트
 *
 * 지키려는 규칙(돈 계산)
 * - 차액 = (새 플랜 일일가 - 현재 플랜 일일가) × 남은 일수, 일일가 = 월 가격 / 30 (정수 절삭)
 * - 남은 기간이 없으면(만료/당일) 0원
 * - 다운그레이드(음수 차액)는 0원으로 보정 — 환불이 아니라 청구 방지가 목적
 *
 * calculateProrationAmount 는 기준 시각(now)을 파라미터로 받는 순수 함수라
 * 시간 고정이 가능해 결과가 결정적이다.
 */
@ExtendWith(MockitoExtension.class)
class ProrationPaymentServiceTest {

    @Mock private MembershipPlanRepository planRepository;
    @Mock private MembershipSubscriptionRepository subscriptionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private ProrationPaymentService service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 16, 0, 0);

    private MembershipPlan planWithMonthlyPrice(long amount) {
        MembershipPlan plan = new MembershipPlan();
        plan.setPrice(new Money(amount, "KRW"));
        return plan;
    }

    private MembershipSubscription subscriptionOf(MembershipPlan currentPlan, LocalDateTime endAt) {
        MembershipSubscription sub = new MembershipSubscription();
        sub.setMembershipPlan(currentPlan);
        sub.setEndAt(endAt);
        return sub;
    }

    @Test
    @DisplayName("업그레이드 차액 = 일일 가격 차이 × 남은 일수")
    void upgradeChargesDailyDifferenceTimesRemainingDays() {
        // 월 3000원(일 100원) → 월 6000원(일 200원), 15일 남음 → 100 × 15 = 1500원
        MembershipSubscription sub = subscriptionOf(planWithMonthlyPrice(3000L), NOW.plusDays(15));
        MembershipPlan target = planWithMonthlyPrice(6000L);

        assertThat(service.calculateProrationAmount(sub, target, NOW)).isEqualTo(1500);
    }

    @Test
    @DisplayName("실요금 예시: 9900→14900원, 30일 남음 - 일일가는 정수 절삭으로 계산된다")
    void dailyPriceIsTruncatedToWholeWon() {
        // 일일가: 9900/30=330, 14900/30=496(.67 절삭) → 차이 166 × 30일 = 4980원
        MembershipSubscription sub = subscriptionOf(planWithMonthlyPrice(9900L), NOW.plusDays(30));
        MembershipPlan target = planWithMonthlyPrice(14900L);

        assertThat(service.calculateProrationAmount(sub, target, NOW)).isEqualTo(4980);
    }

    @Test
    @DisplayName("구독이 이미 만료됐으면 차액은 0원 - 지난 기간에 청구하지 않는다")
    void expiredSubscriptionChargesNothing() {
        MembershipSubscription sub = subscriptionOf(planWithMonthlyPrice(3000L), NOW.minusDays(1));
        MembershipPlan target = planWithMonthlyPrice(6000L);

        assertThat(service.calculateProrationAmount(sub, target, NOW)).isZero();
    }

    @Test
    @DisplayName("만료 당일(남은 일수 0)도 차액은 0원")
    void lastDayChargesNothing() {
        MembershipSubscription sub = subscriptionOf(planWithMonthlyPrice(3000L), NOW);
        MembershipPlan target = planWithMonthlyPrice(6000L);

        assertThat(service.calculateProrationAmount(sub, target, NOW)).isZero();
    }

    @Test
    @DisplayName("다운그레이드는 음수가 아니라 0원 - 마이너스 청구(환불) 금지")
    void downgradeNeverGoesNegative() {
        MembershipSubscription sub = subscriptionOf(planWithMonthlyPrice(6000L), NOW.plusDays(15));
        MembershipPlan target = planWithMonthlyPrice(3000L);

        assertThat(service.calculateProrationAmount(sub, target, NOW)).isZero();
    }
}
