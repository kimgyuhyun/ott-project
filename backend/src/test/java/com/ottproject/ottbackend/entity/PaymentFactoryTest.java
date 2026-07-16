package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Payment 정적 팩토리 검증
 *
 * 왜 이 테스트가 필요한가
 * - 팩토리마다 상태(PENDING/SUCCEEDED/FAILED)와 채우는 식별자 필드가 다르다.
 *   SUCCEEDED 는 providerPaymentId, PENDING/FAILED 는 providerSessionId 를 쓰는데
 *   이 대응이 어긋나면 웹훅이 결제를 찾지 못한다.
 * - 금액 하한이 Money 보다 엄격하다(0원 거부). 서비스 테스트는 정상 금액만 넘겨 이 경계를 밟지 않는다.
 */
class PaymentFactoryTest {

    private User user;
    private MembershipPlan plan;
    private Money price;
    private LocalDateTime at;

    @BeforeEach
    void setUp() {
        user = User.createLocalUser("payer@example.com", "password", "결제자");
        plan = MembershipPlan.createBasicPlan("Basic", "기본 플랜", new Money(9900L, "KRW"), 1);
        price = new Money(9900L, "KRW");
        at = LocalDateTime.of(2026, 7, 17, 12, 0);
    }

    @Nested
    @DisplayName("createPendingPayment")
    class Pending {

        @Test
        @DisplayName("PENDING 상태로 세션 ID 를 달고 생성된다")
        void createsPendingWithSessionId() {
            Payment payment = Payment.createPendingPayment(
                    user, plan, PaymentProvider.IMPORT, "  sess_1  ", price);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getProviderSessionId()).isEqualTo("sess_1");
            assertThat(payment.getPaidAt()).isNull();
        }

        @Test
        @DisplayName("세션 ID 가 없으면 거부한다 - 웹훅이 결제를 되찾을 수 없다")
        void rejectsMissingSessionId() {
            assertThatThrownBy(() -> Payment.createPendingPayment(
                    user, plan, PaymentProvider.IMPORT, "  ", price))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("createSucceededPayment")
    class Succeeded {

        @Test
        @DisplayName("SUCCEEDED 상태로 결제 ID 와 완료 시각을 달고 생성된다")
        void createsSucceededWithPaymentId() {
            Payment payment = Payment.createSucceededPayment(
                    user, plan, PaymentProvider.IMPORT, "  pay_1  ", price, at);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(payment.getProviderPaymentId()).isEqualTo("pay_1");
            assertThat(payment.getPaidAt()).isEqualTo(at);
            assertThat(payment.getCompletedAt()).isEqualTo(at);
        }

        @Test
        @DisplayName("결제 ID 나 완료 시각이 없으면 거부한다")
        void rejectsMissingPaymentIdOrPaidAt() {
            assertThatThrownBy(() -> Payment.createSucceededPayment(
                    user, plan, PaymentProvider.IMPORT, null, price, at))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Payment.createSucceededPayment(
                    user, plan, PaymentProvider.IMPORT, "pay_1", price, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("createFailedPayment")
    class Failed {

        @Test
        @DisplayName("FAILED 상태로 실패 시각을 달고 생성되며 결제 완료 시각은 비어 있다")
        void createsFailedWithFailedAt() {
            Payment payment = Payment.createFailedPayment(
                    user, plan, PaymentProvider.IMPORT, "sess_1", price, at);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailedAt()).isEqualTo(at);
            assertThat(payment.getPaidAt()).isNull();
        }

        @Test
        @DisplayName("실패 시각이 없으면 거부한다")
        void rejectsMissingFailedAt() {
            assertThatThrownBy(() -> Payment.createFailedPayment(
                    user, plan, PaymentProvider.IMPORT, "sess_1", price, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("공통 필수값")
    class SharedValidation {

        /**
         * Money 는 0 원을 허용하지만(음수만 거부) 결제는 0 원을 거부한다.
         * 즉 0 원짜리 플랜은 결제 엔티티를 만들 수 없다 — 결제 없이 처리돼야 한다.
         */
        @Test
        @DisplayName("0 원 결제는 거부한다 - Money 보다 엄격한 하한")
        void rejectsZeroAmount() {
            Money free = new Money(0L, "KRW");

            assertThatThrownBy(() -> Payment.createPendingPayment(
                    user, plan, PaymentProvider.IMPORT, "sess_1", free))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("금액이 없으면 거부한다")
        void rejectsNullPrice() {
            assertThatThrownBy(() -> Payment.createPendingPayment(
                    user, plan, PaymentProvider.IMPORT, "sess_1", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("사용자/플랜/제공자가 없으면 거부한다")
        void rejectsMissingOwnerPlanOrProvider() {
            assertThatThrownBy(() -> Payment.createPendingPayment(
                    null, plan, PaymentProvider.IMPORT, "sess_1", price))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Payment.createPendingPayment(
                    user, null, PaymentProvider.IMPORT, "sess_1", price))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Payment.createPendingPayment(
                    user, plan, null, "sess_1", price))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
