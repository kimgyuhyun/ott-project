package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.PaymentMethod;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.mybatis.MembershipSubscriptionQueryMapper;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.PaymentMethodRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * RecurringBillingService.retryBilling 단위 테스트
 *
 * 지키려는 규칙(정기결제 재시도 - RabbitMQ 지연 큐)
 * - 스테일 메시지 가드: 메시지의 attempt 와 구독의 retryCount 가 다르면 건너뛴다
 *   (스윕 배치가 먼저 재시도한 경우 지연 메시지가 뒤늦게 도착해도 중복 청구되면 안 됨)
 * - 이미 복구(ACTIVE)/해지된 구독은 재청구하지 않는다
 * - 실패 시 retryCount 증가 + 다음 지연 메시지 예약, 3회 소진 시 해지 + 안내 메일
 * - 성공 시 구독 연장 + retryCount 리셋
 * - PG 호출 전에 PENDING 을 먼저 기록한다(중복 차단 + 대사 회수 지점)
 * - 승인 여부가 불명이면 던닝을 진행시키지 않는다(다음 시도가 이중 청구가 되므로)
 */
@ExtendWith(MockitoExtension.class)
class RecurringBillingServiceTest {

    @Mock private MembershipSubscriptionRepository subscriptionRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private MembershipNotificationService notificationService;
    @Mock private MembershipSubscriptionQueryMapper membershipSubscriptionQueryMapper;
    @Mock private BillingRetryPublisher billingRetryPublisher;
    @Mock private BillingAttemptRecorder attemptRecorder;

    @InjectMocks
    private RecurringBillingService service;

    private static final long SUB_ID = 10L;
    private static final long PAYMENT_ID = 777L;

    private MembershipSubscription sub;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);
        MembershipPlan plan = new MembershipPlan();
        plan.setId(5L);
        plan.setPrice(new Money(9900L, "KRW"));
        plan.setPeriodMonths(1);

        // PAST_DUE + 자동갱신 + 1차 실패 상태(retryCount=1)의 구독
        sub = new MembershipSubscription();
        sub.setId(SUB_ID);
        sub.setUser(user);
        sub.setMembershipPlan(plan);
        sub.setStatus(MembershipSubscriptionStatus.PAST_DUE);
        sub.setAutoRenew(true);
        sub.setRetryCount(1);
        sub.setEndAt(LocalDateTime.now().plusDays(10));

        // retryBilling 은 단계마다 트랜잭션 경계를 만들려고 자기 자신을 프록시로 호출한다.
        // 단위 테스트에는 프록시가 없으므로 자기 참조를 직접 꽂아준다(트랜잭션은 어차피 no-op).
        ReflectionTestUtils.setField(service, "self", service);
    }

    // 주의: given(...) 인자 안에서 이 헬퍼를 호출하면 안 된다.
    // 헬퍼 내부의 given(method.getProviderMethodId()) 이 바깥 스터빙과 겹쳐
    // UnfinishedStubbingException 이 난다 → 반드시 변수로 먼저 만들고 스터빙에 넘길 것.
    private PaymentMethod savedCard() {
        PaymentMethod method = mock(PaymentMethod.class);
        given(method.getId()).willReturn(100L);
        given(method.getProviderMethodId()).willReturn("pm_1");
        return method;
    }

    /** PG 호출 전에 선기록되는 PENDING 결제(성공 시 이 행이 SUCCEEDED 로 확정된다) */
    private Payment pendingPayment() {
        return Payment.createPendingPayment(
                sub.getUser(), sub.getMembershipPlan(), PaymentProvider.IMPORT,
                "rebill_10_20260808_1_100", new Money(9900L, "KRW"));
    }

    @Test
    @DisplayName("재청구는 비관적 쓰기 락으로 구독을 선점한다 - MQ 중복 배달 직렬화")
    void retryBillingLocksSubscriptionRow() {
        given(subscriptionRepository.findByIdForUpdate(SUB_ID)).willReturn(Optional.of(sub));
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(1L))
                .willReturn(List.of());

        service.retryBilling(SUB_ID, 1);

        // 락 없는 findById 로 읽으면 첫 처리 커밋 전 두 번째 메시지가 가드를 통과해 이중 청구된다
        verify(subscriptionRepository).findByIdForUpdate(SUB_ID);
        verify(subscriptionRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("락 경합(NOWAIT 실패)은 예외를 올리지 않고 skip - 스윕 배치가 복구한다")
    void lockContentionIsSkippedQuietly() {
        given(subscriptionRepository.findByIdForUpdate(SUB_ID))
                .willThrow(new CannotAcquireLockException("could not obtain lock on row"));

        service.retryBilling(SUB_ID, 1); // 예외가 새어나가면 컨슈머가 ERROR 로 남긴다

        verifyNoInteractions(paymentMethodRepository, paymentGateway); // 청구 시도 자체가 없어야 한다
    }

    @Test
    @DisplayName("구독이 없으면 조용히 종료 - 청구 시도 없음")
    void missingSubscriptionIsSkipped() {
        given(subscriptionRepository.findByIdForUpdate(SUB_ID)).willReturn(Optional.empty());

        service.retryBilling(SUB_ID, 1);

        verifyNoInteractions(paymentMethodRepository, paymentGateway);
    }

    @Test
    @DisplayName("이미 복구된(ACTIVE) 구독은 재청구하지 않는다")
    void recoveredSubscriptionIsSkipped() {
        sub.setStatus(MembershipSubscriptionStatus.ACTIVE);
        given(subscriptionRepository.findByIdForUpdate(SUB_ID)).willReturn(Optional.of(sub));

        service.retryBilling(SUB_ID, 1);

        verifyNoInteractions(paymentMethodRepository, paymentGateway);
    }

    @Test
    @DisplayName("스테일 메시지(attempt ≠ retryCount)는 건너뛴다 - 중복 청구 방지")
    void staleRetryMessageIsSkipped() {
        sub.setRetryCount(2); // 스윕이 먼저 재시도해서 카운트가 이미 올라감
        given(subscriptionRepository.findByIdForUpdate(SUB_ID)).willReturn(Optional.of(sub));

        service.retryBilling(SUB_ID, 1); // 뒤늦게 도착한 1차 재시도 메시지

        verifyNoInteractions(paymentMethodRepository, paymentGateway);
    }

    @Test
    @DisplayName("청구 실패(2차) - retryCount 증가 + 다음 지연 재시도 예약")
    void failedChargeSchedulesNextRetry() {
        given(subscriptionRepository.findByIdForUpdate(SUB_ID)).willReturn(Optional.of(sub));
        PaymentMethod card = savedCard(); // given 밖에서 먼저 생성(중첩 스터빙 방지)
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(1L))
                .willReturn(List.of(card));
        given(attemptRecorder.openAttempt(anyLong(), anyLong(), anyLong(), anyString(), any(Money.class)))
                .willReturn(PAYMENT_ID);
        given(paymentGateway.chargeWithSavedMethod(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString()))
                .willThrow(new PaymentGateway.ChargeException(
                        PaymentGateway.FailureType.SOFT_DECLINE, "CARD_DECLINED", "카드 승인 거절"));
        given(billingRetryPublisher.scheduleRetry(SUB_ID, 2)).willReturn(true);

        service.retryBilling(SUB_ID, 1);

        assertThat(sub.getRetryCount()).isEqualTo(2);
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.PAST_DUE);
        assertThat(sub.getLastErrorCode()).isEqualTo("CARD_DECLINED");
        verify(billingRetryPublisher).scheduleRetry(SUB_ID, 2);
        verify(attemptRecorder).markAttemptFailed(eq(PAYMENT_ID), any(LocalDateTime.class)); // 확정 실패는 닫는다
    }

    @Test
    @DisplayName("3회 소진 - 구독 해지 + 자동갱신 중단 + 안내 메일, 더 이상 재시도 예약 없음")
    void thirdFailureCancelsAndNotifies() {
        sub.setRetryCount(2); // 이번이 3번째 시도
        given(subscriptionRepository.findByIdForUpdate(SUB_ID)).willReturn(Optional.of(sub));
        PaymentMethod card = savedCard(); // given 밖에서 먼저 생성(중첩 스터빙 방지)
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(1L))
                .willReturn(List.of(card));
        given(attemptRecorder.openAttempt(anyLong(), anyLong(), anyLong(), anyString(), any(Money.class)))
                .willReturn(PAYMENT_ID);
        given(paymentGateway.chargeWithSavedMethod(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString()))
                .willThrow(new PaymentGateway.ChargeException(
                        PaymentGateway.FailureType.HARD_DECLINE, "CARD_EXPIRED", "카드 만료"));

        service.retryBilling(SUB_ID, 2);

        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.CANCELED);
        assertThat(sub.isAutoRenew()).isFalse();
        verify(notificationService).sendCanceledDueToDunning(sub.getUser(), sub);
        verify(billingRetryPublisher, never()).scheduleRetry(anyLong(), anyInt());
    }

    @Test
    @DisplayName("청구 성공 - 선기록한 PENDING 확정 + 구독 연장(만료일+1개월) + retryCount 리셋")
    void successfulChargeExtendsSubscription() {
        LocalDateTime originalEnd = sub.getEndAt();
        PaymentGateway.ChargeResult cr = new PaymentGateway.ChargeResult();
        cr.providerPaymentId = "imp_retry_1";
        cr.paidAt = LocalDateTime.now();
        cr.receiptUrl = "https://receipt.example/1";
        Payment pending = pendingPayment();
        given(subscriptionRepository.findByIdForUpdate(SUB_ID)).willReturn(Optional.of(sub));
        PaymentMethod card = savedCard(); // given 밖에서 먼저 생성(중첩 스터빙 방지)
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(1L))
                .willReturn(List.of(card));
        given(attemptRecorder.openAttempt(anyLong(), anyLong(), anyLong(), anyString(), any(Money.class)))
                .willReturn(PAYMENT_ID);
        given(paymentGateway.chargeWithSavedMethod(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString()))
                .willReturn(cr);
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(pending));

        service.retryBilling(SUB_ID, 1);

        // 만료 전 성공: 남은 기간을 깎지 않고 기존 만료일에서 1개월 연장돼야 한다
        assertThat(sub.getEndAt()).isEqualTo(originalEnd.plusMonths(1));
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.ACTIVE);
        assertThat(sub.getRetryCount()).isZero();
        // 새 결제 행을 만드는 게 아니라, 선기록해둔 PENDING 을 확정한다
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(pending.getProviderPaymentId()).isEqualTo("imp_retry_1");
        verify(paymentRepository).save(pending);
    }

    @Test
    @DisplayName("PENDING 은 PG 호출 전에 기록된다 - 응답을 못 받아도 대사가 주울 대상이 남는다")
    void pendingIsRecordedBeforeCallingGateway() {
        PaymentGateway.ChargeResult cr = new PaymentGateway.ChargeResult();
        cr.providerPaymentId = "imp_order_1";
        cr.paidAt = LocalDateTime.now();
        given(subscriptionRepository.findByIdForUpdate(SUB_ID)).willReturn(Optional.of(sub));
        PaymentMethod card = savedCard();
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(1L))
                .willReturn(List.of(card));
        given(attemptRecorder.openAttempt(anyLong(), anyLong(), anyLong(), anyString(), any(Money.class)))
                .willReturn(PAYMENT_ID);
        given(paymentGateway.chargeWithSavedMethod(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString()))
                .willReturn(cr);
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(pendingPayment()));

        service.retryBilling(SUB_ID, 1);

        InOrder order = inOrder(attemptRecorder, paymentGateway);
        order.verify(attemptRecorder).openAttempt(anyLong(), anyLong(), anyLong(), anyString(), any(Money.class));
        order.verify(paymentGateway).chargeWithSavedMethod(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("중복 배달 - merchant_uid 선점(유니크 제약)이면 PG 를 호출하지 않고 상태도 그대로")
    void duplicateDeliveryIsBlockedBeforeCharging() {
        given(subscriptionRepository.findByIdForUpdate(SUB_ID)).willReturn(Optional.of(sub));
        // 삽입 단계에서 막히므로 결제수단 토큰(providerMethodId)은 읽히지도 않는다
        PaymentMethod card = mock(PaymentMethod.class);
        given(card.getId()).willReturn(100L);
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(1L))
                .willReturn(List.of(card));
        given(attemptRecorder.openAttempt(anyLong(), anyLong(), anyLong(), anyString(), any(Money.class)))
                .willThrow(new DataIntegrityViolationException("ux_payments_merchant_uid"));
        given(attemptRecorder.isAlreadyDeclined(anyString())).willReturn(false); // 아직 진행 중인 시도

        service.retryBilling(SUB_ID, 1);

        // 이게 이중 청구를 막는 1차 방어선이다 — 첫 트랜잭션 커밋 전이어도 삽입이 먼저 막힌다
        verifyNoInteractions(paymentGateway);
        assertThat(sub.getRetryCount()).isEqualTo(1); // 던닝 상태를 건드리면 안 된다
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.PAST_DUE);
        verifyNoInteractions(billingRetryPublisher);
    }

    @Test
    @DisplayName("이미 거절된 수단으로 재진입 - 중복으로 막지 말고 다음 수단으로 이어간다")
    void resumesWithNextMethodWhenPreviousAttemptAlreadyDeclined() {
        // 직전 실행이 1번 수단으로 시도했다 거절당한 뒤 끊긴 상황(롤링 배포 중 재배달 등).
        // 여기서 DUPLICATE 로 중단하면 스윕이 몇 번을 돌아도 같은 지점에서 멈춰 구독이 고착된다.
        PaymentMethod primary = mock(PaymentMethod.class);
        given(primary.getId()).willReturn(100L);
        PaymentMethod backup = mock(PaymentMethod.class);
        given(backup.getId()).willReturn(200L);
        given(backup.getProviderMethodId()).willReturn("pm_backup");
        PaymentGateway.ChargeResult cr = new PaymentGateway.ChargeResult();
        cr.providerPaymentId = "imp_resumed_1";
        cr.paidAt = LocalDateTime.now();

        given(subscriptionRepository.findByIdForUpdate(SUB_ID)).willReturn(Optional.of(sub));
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(1L))
                .willReturn(List.of(primary, backup));
        given(attemptRecorder.openAttempt(anyLong(), anyLong(), eq(100L), anyString(), any(Money.class)))
                .willThrow(new DataIntegrityViolationException("ux_payments_merchant_uid"));
        given(attemptRecorder.isAlreadyDeclined(anyString())).willReturn(true); // 이미 FAILED 로 닫힌 시도
        given(attemptRecorder.openAttempt(anyLong(), anyLong(), eq(200L), anyString(), any(Money.class)))
                .willReturn(PAYMENT_ID);
        given(paymentGateway.chargeWithSavedMethod(anyString(), eq("pm_backup"), anyString(), anyLong(), anyString(), anyString()))
                .willReturn(cr);
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(pendingPayment()));

        service.retryBilling(SUB_ID, 1);

        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.ACTIVE);
        assertThat(sub.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("승인 여부 불명(타임아웃) - 던닝을 진행시키지 않고 PENDING 을 남겨 대사에 맡긴다")
    void ambiguousChargeDoesNotAdvanceDunning() {
        given(subscriptionRepository.findByIdForUpdate(SUB_ID)).willReturn(Optional.of(sub));
        PaymentMethod card = savedCard();
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(1L))
                .willReturn(List.of(card));
        given(attemptRecorder.openAttempt(anyLong(), anyLong(), anyLong(), anyString(), any(Money.class)))
                .willReturn(PAYMENT_ID);
        given(paymentGateway.chargeWithSavedMethod(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString()))
                .willThrow(new PaymentGateway.ChargeException(
                        PaymentGateway.FailureType.AMBIGUOUS, "NO_RESPONSE", "재청구 응답 확인 불가"));

        service.retryBilling(SUB_ID, 1);

        // retryCount 를 올리면 다음 시도가 새 merchant_uid 로 또 청구된다 - 실제 이중 청구 시나리오
        assertThat(sub.getRetryCount()).isEqualTo(1);
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.PAST_DUE);
        verifyNoInteractions(billingRetryPublisher);
        // PENDING 을 닫지 않아야 대사 배치가 아임포트에 물어보고 확정할 수 있다
        verify(attemptRecorder, never()).markAttemptFailed(anyLong(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("1순위 결제수단 실패 시 다음 수단으로 폴백해 성공한다 - 수단별로 다른 merchant_uid")
    void fallsBackToSecondPaymentMethod() {
        PaymentMethod primary = mock(PaymentMethod.class);
        given(primary.getId()).willReturn(100L);
        given(primary.getProviderMethodId()).willReturn("pm_primary");
        PaymentMethod backup = mock(PaymentMethod.class);
        given(backup.getId()).willReturn(200L);
        given(backup.getProviderMethodId()).willReturn("pm_backup");
        PaymentGateway.ChargeResult cr = new PaymentGateway.ChargeResult();
        cr.providerPaymentId = "imp_backup_1";
        cr.paidAt = LocalDateTime.now();

        given(subscriptionRepository.findByIdForUpdate(SUB_ID)).willReturn(Optional.of(sub));
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(1L))
                .willReturn(List.of(primary, backup)); // 기본 → 보조 순서
        given(attemptRecorder.openAttempt(anyLong(), anyLong(), anyLong(), anyString(), any(Money.class)))
                .willReturn(PAYMENT_ID);
        given(paymentGateway.chargeWithSavedMethod(anyString(), eq("pm_primary"), anyString(), anyLong(), anyString(), anyString()))
                .willThrow(new PaymentGateway.ChargeException(
                        PaymentGateway.FailureType.SOFT_DECLINE, "LIMIT_EXCEEDED", "한도 초과"));
        given(paymentGateway.chargeWithSavedMethod(anyString(), eq("pm_backup"), anyString(), anyLong(), anyString(), anyString()))
                .willReturn(cr);
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(pendingPayment()));

        service.retryBilling(SUB_ID, 1);

        // 보조 수단으로 결제됐으므로 구독은 정상 복구되고 재시도는 예약되지 않아야 한다
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.ACTIVE);
        assertThat(sub.getRetryCount()).isZero();
        verifyNoInteractions(billingRetryPublisher);
        // 아임포트는 실패한 시도에도 merchant_uid 를 소진시킨다. 수단마다 값이 달라야 폴백이 산다.
        verify(attemptRecorder).openAttempt(anyLong(), anyLong(), eq(100L), anyString(), any(Money.class));
        verify(attemptRecorder).openAttempt(anyLong(), anyLong(), eq(200L), anyString(), any(Money.class));
    }

    @Test
    @DisplayName("결제수단이 없으면 PAST_DUE 유지, 재시도 카운트/예약 없음")
    void noPaymentMethodKeepsPastDueWithoutCounting() {
        given(subscriptionRepository.findByIdForUpdate(SUB_ID)).willReturn(Optional.of(sub));
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(1L))
                .willReturn(List.of());

        service.retryBilling(SUB_ID, 1);

        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.PAST_DUE);
        assertThat(sub.getRetryCount()).isEqualTo(1); // 시도 자체가 없었으니 카운트 불변
        verifyNoInteractions(billingRetryPublisher);
    }

    @Test
    @DisplayName("대사 확정(paid) - 새 구독을 만들지 않고 원래 구독을 연장한다")
    void reconcileRebillExtendsOriginalSubscription() {
        LocalDateTime originalEnd = sub.getEndAt();
        Payment pending = pendingPayment();
        ImportPaymentGateway.ReconcileResult r = new ImportPaymentGateway.ReconcileResult();
        r.found = true;
        r.status = "paid";
        r.impUid = "imp_reconciled_1";
        r.amount = 9900L;
        given(subscriptionRepository.findByIdForUpdate(SUB_ID)).willReturn(Optional.of(sub));

        boolean resolved = service.reconcileRebillPayment(pending, r, LocalDateTime.now());

        assertThat(resolved).isTrue();
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        // 체크아웃 경로(markSucceededAndProvision)를 태웠다면 여기서 새 구독이 생기고
        // 이 구독은 PAST_DUE 인 채로 던닝을 계속 돌았을 것이다
        assertThat(sub.getEndAt()).isEqualTo(originalEnd.plusMonths(1));
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.ACTIVE);
        assertThat(sub.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("대사 확정 - 금액이 다르면 확정하지 않는다")
    void reconcileRebillRejectsAmountMismatch() {
        Payment pending = pendingPayment();
        ImportPaymentGateway.ReconcileResult r = new ImportPaymentGateway.ReconcileResult();
        r.found = true;
        r.status = "paid";
        r.impUid = "imp_reconciled_2";
        r.amount = 100L; // 기대 9900원과 불일치

        boolean resolved = service.reconcileRebillPayment(pending, r, LocalDateTime.now());

        assertThat(resolved).isFalse();
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(subscriptionRepository);
    }
}
