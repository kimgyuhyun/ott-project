package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PlanChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MembershipSubscription 해지 전이가 플랜 변경 예약을 어떻게 다루는지 검증
 *
 * 왜 이 테스트가 필요한가
 * - 플랜 변경 예약(nextPlan/planChangeScheduledAt/changeType)은 배치가 읽어 플랜을 갈아끼우는 신호다.
 *   해지 전이가 이 예약을 남기면, 끝난 구독의 플랜이 바뀌고 변경 안내 메일까지 발송된다.
 *   탈퇴로 익명화된 계정에도 나갔다.
 * - 세 전이의 처리가 서로 다르고, 그 차이가 의도된 것이라 고정해둔다.
 *
 * 지키려는 규칙
 * - CANCELED 로 끝내는 전이(즉시 해지, 던닝 소진 해지)는 예약을 지운다. resume 이 ACTIVE +
 *   cancelAtPeriodEnd 인 구독만 받으므로 되살아날 수 없는 종착 상태다.
 * - 말일 해지 예약은 예약을 남긴다. 재개가 가능한 상태이고, 배치가 집지 않는 것은 쿼리 조건이 맡는다.
 */
class MembershipSubscriptionCancellationTest {

    private MembershipSubscription subscription;
    private MembershipPlan nextPlan;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 9, 12, 0);

    @BeforeEach
    void setUp() {
        User user = User.createLocalUser("subscriber@example.com", "password", "구독자");
        MembershipPlan plan = MembershipPlan.createBasicPlan("Basic", "기본 플랜", new Money(9900L, "KRW"), 1);
        nextPlan = MembershipPlan.createBasicPlan("Lite", "저가 플랜", new Money(5900L, "KRW"), 1);
        subscription = MembershipSubscription.createSubscription(user, plan, NOW.minusDays(30), NOW.plusDays(1));
        subscription.schedulePlanChange(nextPlan, NOW.plusDays(1), PlanChangeType.DOWNGRADE);
    }

    @Test
    @DisplayName("즉시 해지는 플랜 변경 예약을 함께 지운다 - 끝난 구독의 플랜이 바뀌면 안 된다")
    void immediateCancellationClearsScheduledPlanChange() {
        subscription.applyImmediateCancellation(NOW);

        assertThat(subscription.getStatus()).isEqualTo(MembershipSubscriptionStatus.CANCELED);
        assertThat(subscription.getNextPlan()).isNull();
        assertThat(subscription.getPlanChangeScheduledAt()).isNull();
        assertThat(subscription.getChangeType()).isNull();
    }

    @Test
    @DisplayName("던닝 소진 해지도 플랜 변경 예약을 함께 지운다")
    void dunningExhaustedCancellationClearsScheduledPlanChange() {
        subscription.cancelAfterDunningExhausted(NOW);

        assertThat(subscription.getStatus()).isEqualTo(MembershipSubscriptionStatus.CANCELED);
        assertThat(subscription.getNextPlan()).isNull();
        assertThat(subscription.getPlanChangeScheduledAt()).isNull();
        assertThat(subscription.getChangeType()).isNull();
    }

    @Test
    @DisplayName("말일 해지 예약은 플랜 변경 예약을 남긴다 - 재개하면 되살아나야 한다")
    void scheduledCancellationKeepsScheduledPlanChange() {
        subscription.scheduleCancellationAtPeriodEnd();

        // 상태는 ACTIVE 그대로다. 배치가 이 구독을 집지 않는 것은 쿼리의 cancel_at_period_end 조건이 맡는다
        assertThat(subscription.getStatus()).isEqualTo(MembershipSubscriptionStatus.ACTIVE);
        assertThat(subscription.getNextPlan()).isEqualTo(nextPlan);
        assertThat(subscription.getPlanChangeScheduledAt()).isEqualTo(NOW.plusDays(1));
        assertThat(subscription.getChangeType()).isEqualTo(PlanChangeType.DOWNGRADE);
    }
}
