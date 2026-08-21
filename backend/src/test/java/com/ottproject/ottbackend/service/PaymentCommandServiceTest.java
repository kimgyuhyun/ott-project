package com.ottproject.ottbackend.service;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.dto.PaymentCheckoutCreateRequestDto;
import com.ottproject.ottbackend.dto.PaymentCheckoutCreateSuccessResponseDto;
import com.ottproject.ottbackend.dto.PaymentWebhookEventDto;
import com.ottproject.ottbackend.entity.IdempotencyKey;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.PaymentMethod;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PaymentMethodType;
import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.exception.DuplicateWebhookEventException;
import com.ottproject.ottbackend.mybatis.PaymentQueryMapper;
import com.ottproject.ottbackend.repository.IdempotencyKeyRepository;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.OutboxEventRepository;
import com.ottproject.ottbackend.repository.PaymentMethodRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

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
 * SUCCEEDED 는 markSucceededAndProvision(구독 프로비저닝/아웃박스)으로 수렴한다.
 * private 이라 applyWebhookEvent(SUCCEEDED) 경유로 검증한다(가시성 완화 불필요).
 */
@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceTest {

    @Mock
    private MembershipPlanRepository membershipPlanRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private PaymentGateway paymentGateway; // 재검증 경로가 쓰는 계약

    @Mock
    private PlayerProgressReadService playerProgressReadService;

    @Mock
    private MembershipSubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentQueryMapper paymentQueryMapper;

    @Mock
    private PaymentMethodRepository paymentMethodRepository;

    @Mock
    private MembershipCommandService membershipCommandService;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentCommandService service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 16, 12, 0);

    @BeforeEach
    void setUp() {
        // 확정 경로는 단계마다 트랜잭션 경계를 만들려고 자기 자신을 프록시로 호출한다.
        // 단위 테스트에는 프록시가 없으므로 자기 참조를 직접 꽂아준다(트랜잭션은 어차피 no-op).
        ReflectionTestUtils.setField(service, "self", service);
    }

    private User userWithId(long id) {
        return User.reference(id);
    }

    /** 9900원 월간 BASIC 플랜(테스트가 보는 값은 code 와 price 뿐이다) */
    private MembershipPlan basicPlan() {
        return MembershipPlan.createBasicPlan("BASIC", "기본 플랜", new Money(9900L, "KRW"), 1);
    }

    /** 9900원 PENDING 결제(세션 sess_1, 사용자 1) */
    private Payment pendingPayment() {
        MembershipPlan plan = basicPlan();
        return Payment.createPendingPayment(
                userWithId(1L), plan, PaymentProvider.IMPORT, "sess_1", new Money(9900L, "KRW"));
    }

    private PaymentWebhookEventDto event(PaymentStatus status) {
        PaymentWebhookEventDto e = new PaymentWebhookEventDto();
        e.eventId = "evt-1";
        e.status = status;
        e.occurredAt = NOW;
        return e;
    }

    private MembershipSubscription activeSubscription() {
        MembershipPlan plan = basicPlan();
        // 팩토리가 ACTIVE + autoRenew=true 로 만든다
        return MembershipSubscription.createSubscription(userWithId(1L), plan, NOW.minusDays(10), NOW.plusDays(20));
    }

    @Test
    @DisplayName("같은 eventId 는 두 번 처리되지 않는다(멱등) - 결제 조회조차 하지 않음")
    void duplicateEventIdIsIgnored() {
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.of(mock(IdempotencyKey.class)));

        service.applyWebhookEvent(1L, event(PaymentStatus.FAILED));

        verify(paymentRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("금액이 결제 레코드와 다르면 400 거부 - 위조 웹훅 방어(멱등키도 저장 안 함)")
    void amountMismatchIsRejected() {
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(pendingPayment()));
        PaymentWebhookEventDto e = event(PaymentStatus.FAILED);
        e.amount = 5000L; // 실제 결제는 9900원

        assertThatThrownBy(() -> service.applyWebhookEvent(1L, e))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("amount mismatch");
        // 거부된 웹훅은 멱등키를 남기지 않아야 결제사 재전송을 다시 검증할 수 있다
        // (선삽입은 금액·통화 검증 뒤에 있으므로 위조 웹훅은 삽입에 닿기 전에 튕긴다)
        verify(idempotencyKeyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("통화가 다르면 400 거부 - 통화 바꿔치기 방어")
    void currencyMismatchIsRejected() {
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(pendingPayment()));
        PaymentWebhookEventDto e = event(PaymentStatus.FAILED);
        e.currency = "USD"; // 실제 결제는 KRW

        assertThatThrownBy(() -> service.applyWebhookEvent(1L, e))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("currency mismatch");
        verify(idempotencyKeyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("세션ID가 다르면 400 거부 - 다른 결제건의 웹훅 오적용 방어")
    void sessionMismatchIsRejected() {
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(pendingPayment()));
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
        given(paymentRepository.findByIdForUpdate(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyWebhookEvent(999L, event(PaymentStatus.FAILED)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("FAILED 웹훅 - 결제는 FAILED, 활성 구독은 PAST_DUE 로 전환된다")
    void failedTransitionsPaymentAndSubscription() {
        Payment payment = pendingPayment();
        MembershipSubscription sub = activeSubscription();
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));
        given(subscriptionRepository.findActiveEffectiveByUser(1L, MembershipSubscriptionStatus.ACTIVE, NOW))
                .willReturn(Optional.of(sub));

        service.applyWebhookEvent(1L, event(PaymentStatus.FAILED));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailedAt()).isEqualTo(NOW);
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.PAST_DUE);
        verify(idempotencyKeyRepository).saveAndFlush(any()); // 처리 전 멱등키 선삽입
    }

    @Test
    @DisplayName("멱등키 선삽입이 유니크 제약에 걸리면 전용 예외로 좁혀 던진다 - 컨트롤러가 200 으로 흡수할 대상")
    void concurrentWebhookLosesRaceOnIdempotencyKey() {
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty()); // 빠른 경로는 통과
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(pendingPayment()));
        // 같은 eventId 를 다른 요청이 먼저 넣어 커밋한 상황
        given(idempotencyKeyRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("ux_idempotency_key"));

        assertThatThrownBy(() -> service.applyWebhookEvent(1L, event(PaymentStatus.FAILED)))
                .isInstanceOf(DuplicateWebhookEventException.class); // DataIntegrityViolationException 그대로 새면 안 된다

        // 선삽입이 실패했으므로 상태 전이는 시작조차 하지 않는다
        verify(subscriptionRepository, never()).findActiveEffectiveByUser(anyLong(), any(), any());
    }

    @Test
    @DisplayName("CANCELED 웹훅 - 즉시 해지가 아니라 자동갱신 중단 + 말일 해지 예약")
    void canceledStopsAutoRenewButKeepsSubscriptionUntilPeriodEnd() {
        Payment payment = pendingPayment();
        MembershipSubscription sub = activeSubscription();
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));
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
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));
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
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(pendingPayment()));

        assertThatThrownBy(() -> service.applyWebhookEvent(1L, event(PaymentStatus.PENDING)))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ===== SUCCEEDED 확정/지급(markSucceededAndProvision) =====

    /** SUCCEEDED 웹훅(imp_uid/영수증 포함) */
    private PaymentWebhookEventDto succeededEvent() {
        PaymentWebhookEventDto e = event(PaymentStatus.SUCCEEDED);
        e.providerPaymentId = "imp_1";
        e.receiptUrl = "https://receipt.test/1";
        return e;
    }

    // ===== 빌링키와 저장 결제수단 =====
    //
    // customer_uid 는 우리가 지은 이름일 뿐이고 실제 빌링키는 게이트웨이가 들고 있다. 그래서 발급 여부는
    // 물어봐야만 알 수 있는데, 예전 코드는 묻지 않고 체크아웃 시점에 "temp_" + 시각을 자리표로 등록했다.
    // 그 값이 사용자 1번에만 41행 쌓였고 자동 청구가 전부 거절당했다(payments 82행 FAILED).
    // 아래 테스트가 지키는 것은 둘이다: "확인된 것만 등록한다", 그리고 "그 확인을 락 안에서 하지 않는다".

    @Test
    @DisplayName("빌링키가 확인되면 저장 결제수단으로 등록하고 결제에 연결한다")
    void billingKeyIsRegisteredAsPaymentMethod() {
        Payment payment = pendingPayment();
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(paymentGateway.hasBillingKey("ott_billing_1")).willReturn(true);
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(1L))
                .willReturn(List.of());

        service.attachBillingKeyIfIssued(1L, 1L);

        ArgumentCaptor<PaymentMethod> saved = ArgumentCaptor.forClass(PaymentMethod.class);
        verify(paymentMethodRepository).save(saved.capture());
        // 자동 청구가 이 값을 그대로 customer_uid 로 보낸다. 게이트웨이가 빌링키를 묶어 둔 이름이어야 한다
        assertThat(saved.getValue().getProviderMethodId()).isEqualTo("ott_billing_1");
        assertThat(payment.getPaymentMethod()).isNotNull();
    }

    @Test
    @DisplayName("빌링키가 없으면 결제수단을 등록하지 않는다 - 청구 못 하는 값을 저장수단으로 남기지 않는다")
    void noPaymentMethodIsRegisteredWithoutBillingKey() {
        // 단건 채널로 결제됐거나 발급이 실패했다 - 어느 쪽이든 재청구에 쓸 수 없다
        given(paymentGateway.hasBillingKey("ott_billing_1")).willReturn(false);

        service.attachBillingKeyIfIssued(1L, 1L);

        verify(paymentMethodRepository, never()).save(any());
        verifyNoInteractions(paymentRepository); // 물어보고 아니면 DB 를 건드리지 않는다
    }

    @Test
    @DisplayName("이미 결제수단이 연결된 결제는 다시 등록하지 않는다 - 웹훅 재전송·대사가 같은 자리를 다시 밟는다")
    void alreadyAttachedPaymentIsNotRegisteredAgain() {
        Payment payment = pendingPayment();
        payment.attachPaymentMethod(PaymentMethod.createPaymentMethod(
                userWithId(1L), PaymentProvider.IMPORT, PaymentMethodType.KAKAO_PAY, "ott_billing_1"));
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(paymentGateway.hasBillingKey("ott_billing_1")).willReturn(true);

        service.attachBillingKeyIfIssued(1L, 1L);

        verify(paymentMethodRepository, never()).save(any());
    }

    @Test
    @DisplayName("확정 트랜잭션은 게이트웨이를 부르지 않는다 - 결제 행 락을 쥔 채 외부 응답을 기다리면 안 된다")
    void confirmTransactionMakesNoGatewayCall() {
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(pendingPayment()));

        service.applyWebhookEvent(1L, succeededEvent());

        // 빌링키 확인도 결제수단 상세 조회도 이 안에서 하지 않는다(ARCHITECTURE 4절).
        // 빌링키 확인은 확정이 끝난 뒤 attachBillingKeyIfIssued 가 트랜잭션 밖에서 한다.
        verifyNoInteractions(paymentGateway);
    }

    @Test
    @DisplayName("SUCCEEDED 확정 - 결제를 확정하고 멤버십을 지급하며 아웃박스에 1건 적재한다")
    void succeededConfirmsPaymentAndProvisionsMembership() {
        Payment payment = pendingPayment();
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));

        service.applyWebhookEvent(1L, succeededEvent());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getPaidAt()).isEqualTo(NOW);
        assertThat(payment.getProviderPaymentId()).isEqualTo("imp_1");
        assertThat(payment.getReceiptUrl()).isEqualTo("https://receipt.test/1");
        verify(membershipCommandService).subscribe(eq(1L), any());
        verify(outboxEventRepository).save(any());
    }

    @Test
    @DisplayName("멱등 - 이미 SUCCEEDED 인 결제는 멤버십을 재지급하지 않는다(중복 지급 방지)")
    void alreadySucceededPaymentIsNotProvisionedAgain() {
        Payment payment = pendingPayment();
        // 클라 확정/이전 웹훅으로 이미 확정됨
        payment.applyGatewaySuccess(payment.getProviderPaymentId(), null, NOW.minusMinutes(5));
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));

        // eventId 가 다른(=멱등키로 못 거르는) 재전송 웹훅도 여기서 막혀야 한다
        service.applyWebhookEvent(1L, succeededEvent());

        // 핵심: 한 번 결제로 구독 기간이 두 번 늘어나면 안 된다
        verify(membershipCommandService, never()).subscribe(anyLong(), any());
        verify(outboxEventRepository, never()).save(any());
        // 최초 확정 시각도 덮어쓰지 않아야 한다
        assertThat(payment.getPaidAt()).isEqualTo(NOW.minusMinutes(5));
    }

    @Test
    @DisplayName("구독 지급이 실패하면 예외를 전파한다 - 결제만 SUCCEEDED 로 남는 것을 막는다")
    void provisioningFailurePropagatesSoPaymentRollsBack() {
        Payment payment = pendingPayment();
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));
        org.mockito.BDDMockito.willThrow(new RuntimeException("subscribe boom"))
                .given(membershipCommandService)
                .subscribe(anyLong(), any());

        // 과거 회귀: 리스너의 블랭킷 catch 로 구독 생성 실패가 묻혀 돈만 받고 혜택이 안 나갔다
        assertThatThrownBy(() -> service.applyWebhookEvent(1L, succeededEvent()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("멤버십 구독 생성 실패");
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("아웃박스 적재가 실패하면 예외를 전파한다 - 부수효과 유실 방지(함께 롤백)")
    void outboxFailurePropagates() {
        Payment payment = pendingPayment();
        given(idempotencyKeyRepository.findByKeyValue("evt-1")).willReturn(Optional.empty());
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));
        given(outboxEventRepository.save(any())).willThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.applyWebhookEvent(1L, succeededEvent()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("아웃박스 이벤트 적재 실패");
    }

    // ===== 웹훅 진입점(processWebhook): 실패/취소 웹훅 위조 방어 =====
    //
    // 공격 시나리오: merchant_uid만 맞히면 서명 없는 웹훅으로 남의 결제를 FAILED 로 만들어
    // 구독을 PAST_DUE 로 떨어뜨리고 시청을 차단할 수 있었다(성공 웹훅만 API 재검증했음).

    private String iamportBody(String status) {
        return "{\"imp_uid\":\"imp_1\",\"merchant_uid\":\"sess_1\",\"status\":\"" + status + "\"}";
    }

    private PaymentGateway.ReconcileResult reconcile(boolean found, PaymentGateway.ReconcileStatus status) {
        PaymentGateway.ReconcileResult r = new PaymentGateway.ReconcileResult();
        r.found = found;
        r.status = status;
        return r;
    }

    @Test
    @DisplayName("위조 failed 웹훅 - 아임포트 실제 상태가 paid 면 400 거부(결제/구독 손대지 않음)")
    void forgedFailedWebhookIsRejected() {
        given(paymentGateway.verifyWebhookBasicValidation(any(), any())).willReturn(true);
        given(paymentGateway.findPaymentBySessionId("sess_1"))
                .willReturn(reconcile(true, PaymentGateway.ReconcileStatus.PAID));

        assertThatThrownBy(() -> service.processWebhook(new HttpHeaders(), iamportBody("failed")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("웹훅 재검증 실패");

        verifyNoInteractions(subscriptionRepository, paymentRepository);
    }

    @Test
    @DisplayName("아임포트에 결제 기록이 없으면 failed 웹훅을 거부한다(fail-closed)")
    void unverifiableFailedWebhookIsRejected() {
        given(paymentGateway.verifyWebhookBasicValidation(any(), any())).willReturn(true);
        given(paymentGateway.findPaymentBySessionId("sess_1")).willReturn(reconcile(false, null));

        assertThatThrownBy(() -> service.processWebhook(new HttpHeaders(), iamportBody("failed")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("웹훅 재검증 실패");

        verifyNoInteractions(subscriptionRepository, paymentRepository);
    }

    @Test
    @DisplayName("정상 failed 웹훅 - 아임포트 실제 상태와 일치하면 전이된다(재검증이 정상 흐름을 막지 않음)")
    void genuineFailedWebhookIsApplied() {
        Payment payment = pendingPayment();
        ReflectionTestUtils.setField(payment, "id", 1L); // PK 는 영속화가 채우는 값이라 테스트에서만 주입
        MembershipSubscription sub = activeSubscription();
        given(paymentGateway.verifyWebhookBasicValidation(any(), any())).willReturn(true);
        given(paymentGateway.findPaymentBySessionId("sess_1"))
                .willReturn(reconcile(true, PaymentGateway.ReconcileStatus.FAILED));
        given(paymentQueryMapper.findByProviderSessionId("sess_1")).willReturn(payment);
        given(idempotencyKeyRepository.findByKeyValue("imp_1:FAILED")).willReturn(Optional.empty());
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.of(sub));

        service.processWebhook(new HttpHeaders(), iamportBody("failed"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.PAST_DUE);
    }

    @Test
    @DisplayName("위조 cancelled 웹훅 - 아임포트가 paid 라고 하면 400 거부(임의 해지 예약 방어)")
    void forgedCanceledWebhookIsRejected() {
        given(paymentGateway.verifyWebhookBasicValidation(any(), any())).willReturn(true);
        given(paymentGateway.findPaymentBySessionId("sess_1"))
                .willReturn(reconcile(true, PaymentGateway.ReconcileStatus.PAID));

        assertThatThrownBy(() -> service.processWebhook(new HttpHeaders(), iamportBody("cancelled")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("웹훅 재검증 실패");

        verifyNoInteractions(subscriptionRepository, paymentRepository);
    }

    @Test
    @DisplayName("아임포트 웹훅 멱등키 - imp_uid 단독이 아니라 (imp_uid, status) 조합이어야 한다")
    void iamportWebhookEventIdCombinesImpUidAndStatus() {
        // imp_uid 단독이면 정상적인 paid→cancelled 전이의 두 번째가 "이미 처리됨"으로 삼켜진다
        assertThat(service.parseWebhookPayload(iamportBody("paid")).eventId).isEqualTo("imp_1:SUCCEEDED");
        assertThat(service.parseWebhookPayload(iamportBody("cancelled")).eventId)
                .isEqualTo("imp_1:CANCELED");
    }

    // ===== 환불 정책: 7일 이내 AND 전혀 시청하지 않음 =====

    /** 결제일이 daysAgo 일 전인 SUCCEEDED 결제(사용자 1, 9900원) */
    private Payment succeededPaymentPaidDaysAgo(long daysAgo) {
        MembershipPlan plan = basicPlan();
        Payment payment = Payment.createSucceededPayment(
                userWithId(1L),
                plan,
                PaymentProvider.IMPORT,
                "imp_1",
                new Money(9900L, "KRW"),
                LocalDateTime.now().minusDays(daysAgo));
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
        // 3단계가 락을 잡고 다시 읽는다(1단계의 findById 와 별개다)
        given(paymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));
        given(idempotencyKeyRepository.findByKeyValue("payment.refund:1")).willReturn(Optional.empty());
        given(playerProgressReadService.sumWatchedSecondsSincePaidEpisodes(eq(1L), any()))
                .willReturn(0);
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
    @DisplayName("시청 이력이 있으면 환불 불가 - 콘텐츠 소비 후 환불 방지")
    void refundRejectedWhenWatched() {
        given(paymentRepository.findById(1L)).willReturn(Optional.of(succeededPaymentPaidDaysAgo(1)));
        given(playerProgressReadService.sumWatchedSecondsSincePaidEpisodes(eq(1L), any()))
                .willReturn(1); // 1초

        assertThatThrownBy(() -> service.refundIfEligible(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("시청한 경우 환불이 불가");
        verifyNoInteractions(paymentGateway);
    }

    @Test
    @DisplayName("이미 환불된 결제는 다시 환불할 수 없다 - 중복 환불 방지")
    void cannotRefundTwice() {
        Payment payment = succeededPaymentPaidDaysAgo(1);
        payment.applyGatewayRefund(9900L, LocalDateTime.now()); // 이미 환불됨
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.refundIfEligible(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("환불 대상 결제가 아닙니다");
        verifyNoInteractions(paymentGateway);
    }

    // ===== 체크아웃 - 정기결제 채널과 빌링키 식별자 =====

    private PaymentCheckoutCreateRequestDto kakaoCheckoutReq() {
        PaymentCheckoutCreateRequestDto req = new PaymentCheckoutCreateRequestDto();
        req.planCode = "BASIC";
        req.paymentService = "kakao";
        return req;
    }

    private void givenCheckoutDependencies() {
        MembershipPlan plan = basicPlan();
        given(membershipPlanRepository.findByCode("BASIC")).willReturn(Optional.of(plan));
        PaymentGateway.CheckoutSession session = new PaymentGateway.CheckoutSession();
        session.sessionId = "sess_1";
        given(paymentGateway.createCheckoutSession(any(), any(), any(), any(), any(), anyLong()))
                .willReturn(session);
    }

    @Test
    @DisplayName("카카오 체크아웃은 정기결제 채널과 빌링키 식별자를 내려준다 - 결제창이 빌링키를 발급하는 조건")
    void kakaoCheckoutReturnsSubscriptionChannelAndCustomerUid() {
        givenCheckoutDependencies();

        PaymentCheckoutCreateSuccessResponseDto res = service.checkout(1L, kakaoCheckoutReq());

        // 단건 채널(TC0ONETIME)로 열면 결제는 되지만 빌링키가 발급되지 않는다. 그러면 이후 자동 청구가
        // 통째로 불가능해지는데, 결제는 성공하므로 화면에는 아무 이상이 보이지 않는다.
        assertThat(res.pg).isEqualTo("kakaopay.TCSUBSCRIP");
        // customer_uid 가 없으면 결제창이 빌링키를 발급하지 않는다(단건 결제로 끝난다)
        assertThat(res.customerUid).isEqualTo("ott_billing_1");
    }

    @Test
    @DisplayName("체크아웃은 결제수단을 등록하지 않는다 - 빌링키 확인은 결제 확정 후에만 가능하다")
    void checkoutDoesNotRegisterPaymentMethod() {
        givenCheckoutDependencies();

        service.checkout(1L, kakaoCheckoutReq());
        service.checkout(1L, kakaoCheckoutReq());

        // 예전에는 여기서 매번 "temp_" + 시각을 자리표로 등록했다. 결제창이 열리기도 전이라 성공 여부와
        // 무관하게 쌓였고(사용자 1번에 41행), 빌링키가 묶이지 않은 값이라 자동 청구가 전부 거절당했다.
        verify(paymentMethodRepository, never()).save(any());
    }
}
