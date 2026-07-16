package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MembershipSubscription 정적 팩토리 검증
 *
 * 왜 이 테스트가 필요한가
 * - 기간 역전(endAt < startAt) 거부는 결제 기간 산정의 전제인데, 서비스 테스트는 정상 기간만 넘긴다.
 * - endAt = null(무기한) 허용은 3ea06b8 의 전제다. 여기서 거부로 바뀌면 무기한 구독 자체가 사라진다.
 * - dunning 기본값(retry 0/3)이 틀어지면 정기결제 재시도 횟수가 조용히 바뀐다.
 */
class MembershipSubscriptionFactoryTest {

    private User user;
    private MembershipPlan plan;
    private LocalDateTime startAt;

    @BeforeEach
    void setUp() {
        user = User.createLocalUser("subscriber@example.com", "password", "구독자");
        plan = MembershipPlan.createBasicPlan("Basic", "기본 플랜", new Money(9900L, "KRW"), 1);
        startAt = LocalDateTime.of(2026, 7, 17, 12, 0);
    }

    @Test
    @DisplayName("구독은 ACTIVE 자동갱신 상태로 시작하고 재시도 정책 기본값을 갖는다")
    void appliesDefaults() {
        MembershipSubscription subscription =
                MembershipSubscription.createSubscription(user, plan, startAt, startAt.plusMonths(1));

        assertThat(subscription.getStatus()).isEqualTo(MembershipSubscriptionStatus.ACTIVE);
        assertThat(subscription.isAutoRenew()).isTrue();
        assertThat(subscription.isCancelAtPeriodEnd()).isFalse();
        assertThat(subscription.getRetryCount()).isZero();
        assertThat(subscription.getMaxRetry()).isEqualTo(3);
    }

    @Test
    @DisplayName("종료 시각이 없는 무기한 구독을 허용한다")
    void allowsOpenEndedSubscription() {
        MembershipSubscription subscription =
                MembershipSubscription.createSubscription(user, plan, startAt, null);

        assertThat(subscription.getEndAt()).isNull();
        assertThat(subscription.getStatus()).isEqualTo(MembershipSubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("종료 시각이 시작 시각보다 앞서면 거부한다")
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> MembershipSubscription.createSubscription(
                user, plan, startAt, startAt.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("시작과 종료가 같은 순간이면 허용한다 - 역전이 아니다")
    void allowsSameStartAndEnd() {
        assertThatCode(() -> MembershipSubscription.createSubscription(user, plan, startAt, startAt))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("사용자/플랜/시작 시각이 없으면 거부한다")
    void rejectsMissingRequiredFields() {
        assertThatThrownBy(() -> MembershipSubscription.createSubscription(null, plan, startAt, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipSubscription.createSubscription(user, null, startAt, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipSubscription.createSubscription(user, plan, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
