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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
}
