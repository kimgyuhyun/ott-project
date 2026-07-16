package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.PaymentMethod;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.mybatis.MembershipSubscriptionQueryMapper;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.PaymentMethodRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
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

    @InjectMocks
    private RecurringBillingService service;

    private static final long SUB_ID = 10L;

    private MembershipSubscription sub;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);
        MembershipPlan plan = new MembershipPlan();
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
    }

    // 주의: given(...) 인자 안에서 이 헬퍼를 호출하면 안 된다.
    // 헬퍼 내부의 given(method.getProviderMethodId()) 이 바깥 스터빙과 겹쳐
    // UnfinishedStubbingException 이 난다 → 반드시 변수로 먼저 만들고 스터빙에 넘길 것.
    private PaymentMethod savedCard() {
        PaymentMethod method = mock(PaymentMethod.class);
        given(method.getProviderMethodId()).willReturn("pm_1");
        return method;
    }

    @Test
    @DisplayName("구독이 없으면 조용히 종료 - 청구 시도 없음")
    void missingSubscriptionIsSkipped() {
        given(subscriptionRepository.findById(SUB_ID)).willReturn(Optional.empty());

        service.retryBilling(SUB_ID, 1);

        verifyNoInteractions(paymentMethodRepository, paymentGateway);
    }

    @Test
    @DisplayName("이미 복구된(ACTIVE) 구독은 재청구하지 않는다")
    void recoveredSubscriptionIsSkipped() {
        sub.setStatus(MembershipSubscriptionStatus.ACTIVE);
        given(subscriptionRepository.findById(SUB_ID)).willReturn(Optional.of(sub));

        service.retryBilling(SUB_ID, 1);

        verifyNoInteractions(paymentMethodRepository, paymentGateway);
    }

    @Test
    @DisplayName("스테일 메시지(attempt ≠ retryCount)는 건너뛴다 - 중복 청구 방지")
    void staleRetryMessageIsSkipped() {
        sub.setRetryCount(2); // 스윕이 먼저 재시도해서 카운트가 이미 올라감
        given(subscriptionRepository.findById(SUB_ID)).willReturn(Optional.of(sub));

        service.retryBilling(SUB_ID, 1); // 뒤늦게 도착한 1차 재시도 메시지

        verifyNoInteractions(paymentMethodRepository, paymentGateway);
    }

    @Test
    @DisplayName("청구 실패(2차) - retryCount 증가 + 다음 지연 재시도 예약")
    void failedChargeSchedulesNextRetry() {
        given(subscriptionRepository.findById(SUB_ID)).willReturn(Optional.of(sub));
        PaymentMethod card = savedCard(); // given 밖에서 먼저 생성(중첩 스터빙 방지)
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(1L))
                .willReturn(List.of(card));
        given(paymentGateway.chargeWithSavedMethod(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .willThrow(new PaymentGateway.ChargeException(
                        PaymentGateway.FailureType.SOFT_DECLINE, "CARD_DECLINED", "카드 승인 거절"));
        given(billingRetryPublisher.scheduleRetry(SUB_ID, 2)).willReturn(true);

        service.retryBilling(SUB_ID, 1);

        assertThat(sub.getRetryCount()).isEqualTo(2);
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.PAST_DUE);
        assertThat(sub.getLastErrorCode()).isEqualTo("CARD_DECLINED");
        verify(billingRetryPublisher).scheduleRetry(SUB_ID, 2);
    }

    @Test
    @DisplayName("3회 소진 - 구독 해지 + 자동갱신 중단 + 안내 메일, 더 이상 재시도 예약 없음")
    void thirdFailureCancelsAndNotifies() {
        sub.setRetryCount(2); // 이번이 3번째 시도
        given(subscriptionRepository.findById(SUB_ID)).willReturn(Optional.of(sub));
        PaymentMethod card = savedCard(); // given 밖에서 먼저 생성(중첩 스터빙 방지)
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(1L))
                .willReturn(List.of(card));
        given(paymentGateway.chargeWithSavedMethod(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .willThrow(new PaymentGateway.ChargeException(
                        PaymentGateway.FailureType.HARD_DECLINE, "CARD_EXPIRED", "카드 만료"));

        service.retryBilling(SUB_ID, 2);

        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.CANCELED);
        assertThat(sub.isAutoRenew()).isFalse();
        verify(notificationService).sendCanceledDueToDunning(sub.getUser(), sub);
        verify(billingRetryPublisher, never()).scheduleRetry(anyLong(), anyInt());
    }

    @Test
    @DisplayName("청구 성공 - 구독 연장(만료일+1개월) + ACTIVE 복구 + retryCount 리셋")
    void successfulChargeExtendsSubscription() {
        LocalDateTime originalEnd = sub.getEndAt();
        PaymentGateway.ChargeResult cr = new PaymentGateway.ChargeResult();
        cr.providerPaymentId = "imp_retry_1";
        cr.paidAt = LocalDateTime.now();
        cr.receiptUrl = "https://receipt.example/1";
        given(subscriptionRepository.findById(SUB_ID)).willReturn(Optional.of(sub));
        PaymentMethod card = savedCard(); // given 밖에서 먼저 생성(중첩 스터빙 방지)
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(1L))
                .willReturn(List.of(card));
        given(paymentGateway.chargeWithSavedMethod(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .willReturn(cr);

        service.retryBilling(SUB_ID, 1);

        // 만료 전 성공: 남은 기간을 깎지 않고 기존 만료일에서 1개월 연장돼야 한다
        assertThat(sub.getEndAt()).isEqualTo(originalEnd.plusMonths(1));
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.ACTIVE);
        assertThat(sub.getRetryCount()).isZero();
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("결제수단이 없으면 PAST_DUE 유지, 재시도 카운트/예약 없음")
    void noPaymentMethodKeepsPastDueWithoutCounting() {
        given(subscriptionRepository.findById(SUB_ID)).willReturn(Optional.of(sub));
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(1L))
                .willReturn(List.of());

        service.retryBilling(SUB_ID, 1);

        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.PAST_DUE);
        assertThat(sub.getRetryCount()).isEqualTo(1); // 시도 자체가 없었으니 카운트 불변
        verifyNoInteractions(billingRetryPublisher);
    }
}
