package com.ottproject.ottbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.dto.PaymentWebhookEventDto;
import com.ottproject.ottbackend.entity.IdempotencyKey;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.mybatis.PaymentQueryMapper;
import com.ottproject.ottbackend.repository.IdempotencyKeyRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * PaymentCommandService.applyWebhookEvent 단위 테스트
 *
 * 지키려는 규칙(웹훅 상태 전이)
 * - 같은 eventId 는 두 번 처리되지 않는다(멱등) — 결제사 웹훅은 중복 전송이 정상 동작이다
 * - 금액이 결제 레코드와 다르면 거부한다 — 위조/변조 웹훅 방어
 * - FAILED → 결제 FAILED + 활성 구독 PAST_DUE(재시도는 배치가 수행)
 * - CANCELED → 자동갱신 중단 + 말일 해지 예약(즉시 해지 아님)
 * - REFUNDED → 결제 REFUNDED + 구독 즉시 해지(정책)
 *
 * SUCCEEDED 는 markSucceededAndProvision(구독 프로비저닝/아웃박스)으로 수렴하는 무거운
 * 경로라 이 단위 테스트 범위에서 제외한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceTest {

    @Mock private MembershipPlanRepository membershipPlanRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private PlayerProgressReadService playerProgressReadService;
    @Mock private MembershipSubscriptionRepository subscriptionRepository;
    @Mock private PaymentQueryMapper paymentQueryMapper;
    @Mock private PaymentMethodService paymentMethodService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private MembershipCommandService membershipCommandService;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentCommandService service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 16, 12, 0);

    private User userWithId(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    /** 9900원 PENDING 결제(세션 sess_1, 사용자 1) */
    private Payment pendingPayment() {
        MembershipPlan plan = new MembershipPlan();
        plan.setPrice(new Money(9900L, "KRW"));
        return Payment.createPendingPayment(userWithId(1L), plan, PaymentProvider.IMPORT,
                "sess_1", new Money(9900L, "KRW"));
    }

    private PaymentWebhookEventDto event(PaymentStatus status) {
        PaymentWebhookEventDto e = new PaymentWebhookEventDto();
        e.eventId = "evt-1";
        e.status = status;
        e.occurredAt = NOW;
        return e;
    }

    private MembershipSubscription activeSubscription() {
        MembershipSubscription sub = new MembershipSubscription();
        sub.setStatus(MembershipSubscriptionStatus.ACTIVE);
        sub.setAutoRenew(true);
        return sub;
    }

    @Test
    @DisplayName("같은 eventId 는 두 번 처리되지 않는다(멱등) - 결제 조회조차 하지 않음")
    void duplicateEventIdIsIgnored() {
        given(idempotencyKeyRepository.findByKeyValue("evt-1"))
                .willReturn(Optional.of(mock(IdempotencyKey.class)));

        service.applyWebhookEvent(1L, event(PaymentStatus.FAILED));

        verify(paymentRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("금액이 결제 레코드와 다르면 400 거부 - 위조 웹훅 방어(멱등키도 저장 안 함)")
    void amountMismatchIsRejected() {
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findById(1L)).willReturn(Optional.of(pendingPayment()));
        PaymentWebhookEventDto e = event(PaymentStatus.FAILED);
        e.amount = 5000L; // 실제 결제는 9900원

        assertThatThrownBy(() -> service.applyWebhookEvent(1L, e))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("amount mismatch");
        // 거부된 웹훅은 멱등키를 남기지 않아야 결제사 재전송을 다시 검증할 수 있다
        verify(idempotencyKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("통화가 다르면 400 거부 - 통화 바꿔치기 방어")
    void currencyMismatchIsRejected() {
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findById(1L)).willReturn(Optional.of(pendingPayment()));
        PaymentWebhookEventDto e = event(PaymentStatus.FAILED);
        e.currency = "USD"; // 실제 결제는 KRW

        assertThatThrownBy(() -> service.applyWebhookEvent(1L, e))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("currency mismatch");
        verify(idempotencyKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("세션ID가 다르면 400 거부 - 다른 결제건의 웹훅 오적용 방어")
    void sessionMismatchIsRejected() {
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findById(1L)).willReturn(Optional.of(pendingPayment()));
        PaymentWebhookEventDto e = event(PaymentStatus.FAILED);
        e.providerSessionId = "sess_other"; // 실제 결제 세션은 sess_1

        assertThatThrownBy(() -> service.applyWebhookEvent(1L, e))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("session mismatch");
    }

    @Test
    @DisplayName("존재하지 않는 결제면 400 거부")
    void unknownPaymentIsRejected() {
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyWebhookEvent(999L, event(PaymentStatus.FAILED)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("FAILED 웹훅 - 결제는 FAILED, 활성 구독은 PAST_DUE 로 전환된다")
    void failedTransitionsPaymentAndSubscription() {
        Payment payment = pendingPayment();
        MembershipSubscription sub = activeSubscription();
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(subscriptionRepository.findActiveEffectiveByUser(1L, MembershipSubscriptionStatus.ACTIVE, NOW))
                .willReturn(Optional.of(sub));

        service.applyWebhookEvent(1L, event(PaymentStatus.FAILED));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailedAt()).isEqualTo(NOW);
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.PAST_DUE);
        verify(idempotencyKeyRepository).save(any()); // 처리 완료 후 멱등키 기록
    }

    @Test
    @DisplayName("CANCELED 웹훅 - 즉시 해지가 아니라 자동갱신 중단 + 말일 해지 예약")
    void canceledStopsAutoRenewButKeepsSubscriptionUntilPeriodEnd() {
        Payment payment = pendingPayment();
        MembershipSubscription sub = activeSubscription();
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(subscriptionRepository.findActiveEffectiveByUser(1L, MembershipSubscriptionStatus.ACTIVE, NOW))
                .willReturn(Optional.of(sub));

        service.applyWebhookEvent(1L, event(PaymentStatus.CANCELED));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(sub.isAutoRenew()).isFalse();
        assertThat(sub.isCancelAtPeriodEnd()).isTrue();
        // 말일까지는 구독 유지: 상태는 그대로 ACTIVE
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("REFUNDED 웹훅 - 환불 금액 기록 + 구독 즉시 해지(정책)")
    void refundedRecordsAmountAndCancelsImmediately() {
        Payment payment = pendingPayment();
        MembershipSubscription sub = activeSubscription();
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(subscriptionRepository.findActiveEffectiveByUser(1L, MembershipSubscriptionStatus.ACTIVE, NOW))
                .willReturn(Optional.of(sub));
        PaymentWebhookEventDto e = event(PaymentStatus.REFUNDED);
        e.amount = 9900L; // 금액 검증 통과 + 환불 금액으로 기록

        service.applyWebhookEvent(1L, e);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(9900L);
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.CANCELED);
        assertThat(sub.isAutoRenew()).isFalse();
        assertThat(sub.getCanceledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("지원하지 않는 상태(PENDING)는 400 거부")
    void unsupportedStatusIsRejected() {
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findById(1L)).willReturn(Optional.of(pendingPayment()));

        assertThatThrownBy(() -> service.applyWebhookEvent(1L, event(PaymentStatus.PENDING)))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ===== 환불 정책: 7일 이내 AND 전혀 시청하지 않음 =====

    /** 결제일이 daysAgo 일 전인 SUCCEEDED 결제(사용자 1, 9900원) */
    private Payment succeededPaymentPaidDaysAgo(long daysAgo) {
        MembershipPlan plan = new MembershipPlan();
        plan.setPrice(new Money(9900L, "KRW"));
        Payment payment = Payment.createSucceededPayment(userWithId(1L), plan, PaymentProvider.IMPORT,
                "imp_1", new Money(9900L, "KRW"), LocalDateTime.now().minusDays(daysAgo));
        payment.setPaidAt(LocalDateTime.now().minusDays(daysAgo));
        return payment;
    }

    @Test
    @DisplayName("환불 성공 - 7일 이내 + 미시청이면 전액 환불되고 구독은 즉시 해지된다")
    void refundSucceedsWithinPolicy() {
        Payment payment = succeededPaymentPaidDaysAgo(3);
        MembershipSubscription sub = activeSubscription();
        PaymentGateway.RefundResult rr = new PaymentGateway.RefundResult();
        rr.refundedAt = LocalDateTime.now();
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(playerProgressReadService.sumWatchedSecondsSincePaidEpisodes(eq(1L), any())).willReturn(0);
        given(paymentGateway.issueRefund("imp_1", 9900L)).willReturn(rr);
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.of(sub));

        service.refundIfEligible(1L, 1L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(9900L); // 전액
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.CANCELED);
        assertThat(sub.isAutoRenew()).isFalse();
    }

    @Test
    @DisplayName("남의 결제는 환불할 수 없다 - 403")
    void cannotRefundOthersPayment() {
        given(paymentRepository.findById(1L)).willReturn(Optional.of(succeededPaymentPaidDaysAgo(1)));

        // 결제 소유자는 1번 사용자인데 2번 사용자가 환불 시도
        assertThatThrownBy(() -> service.refundIfEligible(2L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("본인 결제만");
        verifyNoInteractions(paymentGateway);
    }

    @Test
    @DisplayName("7일 초과면 환불 불가 - 게이트웨이 호출조차 하지 않는다")
    void refundRejectedAfterSevenDays() {
        given(paymentRepository.findById(1L)).willReturn(Optional.of(succeededPaymentPaidDaysAgo(8)));

        assertThatThrownBy(() -> service.refundIfEligible(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("환불 가능 기간을 초과");
        verifyNoInteractions(paymentGateway);
    }

    @Test
    @DisplayName("1초라도 시청했으면 환불 불가 - 콘텐츠 소비 후 환불 방지")
    void refundRejectedWhenWatched() {
        given(paymentRepository.findById(1L)).willReturn(Optional.of(succeededPaymentPaidDaysAgo(1)));
        given(playerProgressReadService.sumWatchedSecondsSincePaidEpisodes(eq(1L), any())).willReturn(1); // 1초

        assertThatThrownBy(() -> service.refundIfEligible(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("시청한 경우 환불이 불가");
        verifyNoInteractions(paymentGateway);
    }

    @Test
    @DisplayName("이미 환불된 결제는 다시 환불할 수 없다 - 중복 환불 방지")
    void cannotRefundTwice() {
        Payment payment = succeededPaymentPaidDaysAgo(1);
        payment.setStatus(PaymentStatus.REFUNDED); // 이미 환불됨
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.refundIfEligible(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("환불 대상 결제가 아닙니다");
        verifyNoInteractions(paymentGateway);
    }
}
