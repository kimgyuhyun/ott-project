package com.ottproject.ottbackend.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.enums.PaymentStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Payment 만료(expireUnpaid) 상태 전이 검증
 *
 * 대사 기간을 넘긴 PENDING 을 닫는 전이다. 결제된 결제를 닫으면 돈을 받고 멤버십을 안 준 기록이 되므로
 * PENDING 에서만 허용하고, 상태와 취소 시각이 함께 바뀌는지 고정한다.
 */
class PaymentExpiryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 12, 0);

    private Payment pendingPayment() {
        MembershipPlan plan = MembershipPlan.createBasicPlan("Basic", "기본 플랜", new Money(9900L, "KRW"), 1);
        return Payment.createPendingPayment(
                User.reference(1L), plan, PaymentProvider.IMPORT, "sess_1", new Money(9900L, "KRW"));
    }

    @Test
    @DisplayName("PENDING 결제는 CANCELED 로 닫히고 취소 시각이 함께 기록된다")
    void expiresPendingPayment() {
        Payment payment = pendingPayment();

        payment.expireUnpaid(NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("PENDING 이 아닌 결제는 닫지 않는다 - 결제된 결제를 만료시키면 돈만 받은 기록이 된다")
    void refusesNonPendingPayment() {
        Payment payment = pendingPayment();
        payment.markAsSucceeded("imp_1", NOW);

        assertThatThrownBy(() -> payment.expireUnpaid(NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }
}
