package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.MembershipCancelMembershipRequestDto;
import com.ottproject.ottbackend.dto.MembershipPlanChangeRequestDto;
import com.ottproject.ottbackend.dto.MembershipPlanChangeResponseDto;
import com.ottproject.ottbackend.dto.MembershipSubscribeRequestDto;
import com.ottproject.ottbackend.entity.IdempotencyKey;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PlanChangeType;
import com.ottproject.ottbackend.exception.DuplicateIdempotentRequestException;
import com.ottproject.ottbackend.repository.IdempotencyKeyRepository;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * MembershipCommandService 구독 생명주기 단위 테스트
 *
 * 지키려는 규칙(해지/재개 정책)
 * - 해지는 "말일 해지"만 지원: 자동갱신만 끊고 만료일까지 혜택 유지(상태는 ACTIVE 그대로)
 *   → 즉시 CANCELED 로 바꾸면 이미 결제한 기간의 혜택을 뺏는 것이라 분쟁이 된다
 * - 해지는 멱등: 같은 idempotencyKey 로 두 번 오면 두 번째는 무시(중복 메일/중복 처리 방지)
 * - 재개는 "해지 예약된 구독"만 가능
 */
@ExtendWith(MockitoExtension.class)
class MembershipCommandServiceTest {

    @Mock private MembershipPlanRepository planRepository;
    @Mock private MembershipSubscriptionRepository subscriptionRepository;
    @Mock private IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock private MembershipNotificationService notificationService;

    @InjectMocks
    private MembershipCommandService service;

    private MembershipSubscription sub;

    @BeforeEach
    void setUp() {
        User user = User.reference(1L);
        // 팩토리가 ACTIVE + autoRenew=true + cancelAtPeriodEnd=false 로 만든다(해지 전 정상 상태)
        sub = MembershipSubscription.createSubscription(
                user, plan("BASIC", 9900L, 1), LocalDateTime.now(), LocalDateTime.now().plusMonths(1));
    }

    private MembershipCancelMembershipRequestDto cancelReq(String idempotencyKey) {
        MembershipCancelMembershipRequestDto req = new MembershipCancelMembershipRequestDto();
        req.idempotencyKey = idempotencyKey;
        return req;
    }

    // ===== 해지 =====

    @Test
    @DisplayName("해지 - 자동갱신만 끊고 말일 해지 예약, 만료일까지 혜택 유지(ACTIVE)")
    void cancelSchedulesAtPeriodEndAndKeepsBenefits() {
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.of(sub));

        service.cancel(1L, cancelReq(null));

        assertThat(sub.isAutoRenew()).isFalse();
        assertThat(sub.isCancelAtPeriodEnd()).isTrue();
        // 핵심: 즉시 해지가 아니다. 이미 결제한 기간의 혜택은 유지돼야 한다
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.ACTIVE);
        verify(notificationService).sendCancelAtPeriodEnd(sub.getUser(), sub);
    }

    @Test
    @DisplayName("해지 멱등 - 같은 키로 다시 오면 아무것도 하지 않는다(중복 메일 방지)")
    void cancelIsIdempotent() {
        given(idempotencyKeyRepository.findByKeyValue("key-1"))
                .willReturn(Optional.of(mock(IdempotencyKey.class)));

        service.cancel(1L, cancelReq("key-1"));

        verifyNoInteractions(subscriptionRepository, notificationService);
    }

    @Test
    @DisplayName("해지 - 멱등키를 처리 '전에' 선삽입한다(제약이 동시 요청을 판정해야 하므로)")
    void cancelInsertsIdempotencyKeyBeforeProcessing() {
        given(idempotencyKeyRepository.findByKeyValue("key-1")).willReturn(Optional.empty());
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.of(sub));

        service.cancel(1L, cancelReq("key-1"));

        // 맨 끝에서 save 하면 동시 요청 둘 다 해지 처리와 메일 발송을 마친 뒤에야 충돌한다.
        // flush 까지 해야 제약 위반이 이 자리에서 잡힌다.
        InOrder order = inOrder(idempotencyKeyRepository, notificationService);
        order.verify(idempotencyKeyRepository).saveAndFlush(any(IdempotencyKey.class));
        order.verify(notificationService).sendCancelAtPeriodEnd(any(), any());
    }

    @Test
    @DisplayName("해지 - 멱등키 선삽입이 제약에 걸리면 중복 요청으로 보고 중단한다(메일 재발송 없음)")
    void cancelStopsWhenIdempotencyKeyAlreadyClaimed() {
        given(idempotencyKeyRepository.findByKeyValue("key-1")).willReturn(Optional.empty()); // 빠른 경로는 통과
        given(idempotencyKeyRepository.saveAndFlush(any(IdempotencyKey.class)))
                .willThrow(new DataIntegrityViolationException("ux_idempotency_key")); // 경합에서 진 쪽

        assertThatThrownBy(() -> service.cancel(1L, cancelReq("key-1")))
                .isInstanceOf(DuplicateIdempotentRequestException.class);

        verifyNoInteractions(subscriptionRepository, notificationService);
    }

    @Test
    @DisplayName("유효한 구독이 없으면 해지할 수 없다 - 400")
    void cancelWithoutSubscriptionIsRejected() {
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(1L, cancelReq(null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("유효한 구독이 없습니다");
        verifyNoInteractions(notificationService);
    }

    // ===== 탈퇴 시 즉시 해지 =====

    @Test
    @DisplayName("탈퇴 즉시 해지 - ACTIVE 구독을 CANCELED + autoRenew off 로 바꾼다(잔여기간 소멸)")
    void cancelImmediatelyForWithdrawalEndsActiveSubscription() {
        given(subscriptionRepository.findAllByUserAndStatusIn(eq(1L), any()))
                .willReturn(List.of(sub));

        service.cancelImmediatelyForWithdrawal(1L);

        // 말일 해지 예약으로 두면 만료일까지 ACTIVE 라, 익명화된 계정이 혜택과 청구 대상을 계속 들고 있게 된다
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.CANCELED);
        assertThat(sub.isAutoRenew()).isFalse();
        assertThat(sub.getCanceledAt()).isNotNull();
        // 이 시점 사용자 이메일은 곧 익명화되므로 안내 메일을 보내면 안 된다
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("탈퇴 즉시 해지 - PAST_DUE 구독도 CANCELED 로 바꿔 던닝 재시도를 멈춘다")
    void cancelImmediatelyForWithdrawalEndsPastDueSubscription() {
        sub.applyPaymentFailure(LocalDateTime.now()); // 결제 실패 → PAST_DUE, 던닝 재시도 중
        given(subscriptionRepository.findAllByUserAndStatusIn(eq(1L), any()))
                .willReturn(List.of(sub));

        service.cancelImmediatelyForWithdrawal(1L);

        // RecurringBillingService.prepareRetry 의 가드가 "PAST_DUE 아니거나 autoRenew=false 면 skip" 이므로
        // 이 두 값이 재시도를 멈추는 근거다. 그대로 두면 익명화된 계정에 계속 결제가 시도된다
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.CANCELED);
        assertThat(sub.isAutoRenew()).isFalse();
    }

    @Test
    @DisplayName("탈퇴 즉시 해지 - 구독이 없으면 예외 없이 아무것도 하지 않는다(구독 없는 사용자도 탈퇴해야 한다)")
    void cancelImmediatelyForWithdrawalWithoutSubscriptionDoesNothing() {
        given(subscriptionRepository.findAllByUserAndStatusIn(eq(1L), any()))
                .willReturn(List.of());

        service.cancelImmediatelyForWithdrawal(1L);

        verifyNoInteractions(notificationService, idempotencyKeyRepository);
    }

    @Test
    @DisplayName("탈퇴 즉시 해지 - 겹치는 구독이 여러 건이면 전부 해지한다(한 건이라도 남으면 청구가 계속된다)")
    void cancelImmediatelyForWithdrawalEndsEveryRemainingSubscription() {
        // 무기한 구독(endAt=null)이 있는 사용자가 재구독하면 subscribe() 가 연장 분기를 타지 못해
        // 겹치는 ACTIVE 구독이 실제로 생긴다. ACTIVE 와 PAST_DUE 가 함께 남는 경우도 같다
        MembershipSubscription older = MembershipSubscription.createSubscription(
                User.reference(1L), plan("BASIC", 9900L, 1), LocalDateTime.now().minusMonths(2), null);
        older.applyPaymentFailure(LocalDateTime.now()); // 던닝 재시도 중인 PAST_DUE
        given(subscriptionRepository.findAllByUserAndStatusIn(eq(1L), any()))
                .willReturn(List.of(sub, older));

        service.cancelImmediatelyForWithdrawal(1L);

        // 남겨두면 autoRenew=true 라 정기결제 배치(ACTIVE/PAST_DUE + autoRenew ON 이 대상)가
        // 익명화된 계정에 계속 청구한다
        assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.CANCELED);
        assertThat(sub.isAutoRenew()).isFalse();
        assertThat(older.getStatus()).isEqualTo(MembershipSubscriptionStatus.CANCELED);
        assertThat(older.isAutoRenew()).isFalse();
    }

    // ===== 재개 =====

    @Test
    @DisplayName("재개 - 해지 예약된 구독의 자동갱신을 되살린다")
    void resumeRestoresAutoRenew() {
        sub.scheduleCancellationAtPeriodEnd(); // 해지 예약 상태
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.of(sub));

        service.resume(1L);

        assertThat(sub.isAutoRenew()).isTrue();
        assertThat(sub.isCancelAtPeriodEnd()).isFalse();
        verify(notificationService).sendResumeNotification(sub.getUser(), sub);
    }

    @Test
    @DisplayName("해지 예약이 아닌 구독은 재개할 수 없다 - 400")
    void resumeOnNonCanceledSubscriptionIsRejected() {
        // setUp 의 구독은 해지 예약된 적이 없다(팩토리 기본값 cancelAtPeriodEnd=false)
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.of(sub));

        assertThatThrownBy(() -> service.resume(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("해지 예약된 멤버십이 아닙니다");
        verify(notificationService, never()).sendResumeNotification(any(), any());
    }

    @Test
    @DisplayName("유효한 구독이 없으면 재개할 수 없다 - 400")
    void resumeWithoutSubscriptionIsRejected() {
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.resume(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("유효한 구독이 없습니다");
    }

    // ===== 구독 신청/연장 =====
    //
    // subscribe 는 결제 성공 확정(markSucceededAndProvision) 뒤에 호출된다.
    // 따라서 "이미 구독이 있으면 거절"이 아니라 "잔여기간 뒤에 이어붙인다"가 옳다.
    // 거절하면 돈은 받고 기간은 주지 않는 셈이 된다.

    private MembershipPlan plan(String code, long price, int periodMonths) {
        // createBasicPlan 이 code 를 이름에서 파생하므로 이름에 코드를 그대로 준다
        MembershipPlan p = MembershipPlan.createBasicPlan(code, code + " 플랜", new Money(price, "KRW"), periodMonths);
        // 가격을 id 로 재활용(플랜 동일성 비교용, 값 자체는 의미 없음). PK 는 영속화가 채우는 값이라 테스트에서만 주입
        ReflectionTestUtils.setField(p, "id", price);
        return p;
    }

    private MembershipSubscribeRequestDto subscribeReq(String planCode) {
        MembershipSubscribeRequestDto req = new MembershipSubscribeRequestDto();
        req.planCode = planCode;
        return req;
    }

    /** subscribe 가 저장한 구독을 꺼낸다 */
    private MembershipSubscription captureSaved() {
        ArgumentCaptor<MembershipSubscription> captor = ArgumentCaptor.forClass(MembershipSubscription.class);
        verify(subscriptionRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("구독 - 존재하지 않는 플랜이면 400, 저장하지 않는다")
    void subscribeWithUnknownPlanIsRejected() {
        given(planRepository.findByCode("NOPE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.subscribe(1L, subscribeReq("NOPE")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("플랜이 존재하지 않습니다");
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("구독 - 기존 구독이 없으면 지금부터 플랜 기간만큼 시작하고 다음 청구일을 종료일에 맞춘다")
    void subscribeWithoutPreviousStartsNow() {
        given(planRepository.findByCode("BASIC")).willReturn(Optional.of(plan("BASIC", 9900L, 1)));
        given(subscriptionRepository.findTopByUser_IdOrderByStartAtDesc(1L)).willReturn(Optional.empty());

        LocalDateTime before = LocalDateTime.now();
        service.subscribe(1L, subscribeReq("BASIC"));

        MembershipSubscription saved = captureSaved();
        assertThat(saved.getStartAt()).isBetween(before, LocalDateTime.now());
        assertThat(saved.getEndAt()).isEqualTo(saved.getStartAt().plusMonths(1));
        assertThat(saved.getNextBillingAt()).isEqualTo(saved.getEndAt());
        assertThat(saved.getUser().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("구독 - 잔여기간이 남은 활성 구독이 있으면 그 종료일 직후부터 이어붙인다(기간 소실 방지)")
    void subscribeExtendsFromRemainingPeriod() {
        LocalDateTime latestEnd = LocalDateTime.now().plusDays(10);
        MembershipSubscription latest = MembershipSubscription.createSubscription(
                User.reference(1L), plan("BASIC", 9900L, 1), LocalDateTime.now().minusDays(20), latestEnd); // ACTIVE + 잔여기간
        given(planRepository.findByCode("BASIC")).willReturn(Optional.of(plan("BASIC", 9900L, 1)));
        given(subscriptionRepository.findTopByUser_IdOrderByStartAtDesc(1L)).willReturn(Optional.of(latest));

        service.subscribe(1L, subscribeReq("BASIC"));

        MembershipSubscription saved = captureSaved();
        // 핵심: 지금부터 시작하면 남아있던 10일이 증발한다
        assertThat(saved.getStartAt()).isEqualTo(latestEnd);
        assertThat(saved.getEndAt()).isEqualTo(latestEnd.plusMonths(1));
    }

    @Test
    @DisplayName("구독 - 이미 만료된 구독은 이어붙이지 않고 지금부터 시작한다")
    void subscribeDoesNotExtendFromExpiredSubscription() {
        MembershipSubscription expired = MembershipSubscription.createSubscription(
                User.reference(1L), plan("BASIC", 9900L, 1),
                LocalDateTime.now().minusMonths(2), LocalDateTime.now().minusDays(1)); // ACTIVE 지만 이미 지남
        given(planRepository.findByCode("BASIC")).willReturn(Optional.of(plan("BASIC", 9900L, 1)));
        given(subscriptionRepository.findTopByUser_IdOrderByStartAtDesc(1L)).willReturn(Optional.of(expired));

        LocalDateTime before = LocalDateTime.now();
        service.subscribe(1L, subscribeReq("BASIC"));

        // 과거 종료일부터 시작하면 결제한 기간이 이미 지난 채로 시작된다
        assertThat(captureSaved().getStartAt()).isBetween(before, LocalDateTime.now());
    }

    @Test
    @DisplayName("구독 - 잔여기간이 남아도 활성 상태가 아니면 이어붙이지 않는다")
    void subscribeDoesNotExtendFromInactiveSubscription() {
        MembershipSubscription canceled = MembershipSubscription.createSubscription(
                User.reference(1L), plan("BASIC", 9900L, 1),
                LocalDateTime.now().minusDays(20), LocalDateTime.now().plusDays(10)); // 날짜상으론 잔여기간이 있음
        canceled.applyImmediateCancellation(LocalDateTime.now()); // 환불 등으로 해지됨
        given(planRepository.findByCode("BASIC")).willReturn(Optional.of(plan("BASIC", 9900L, 1)));
        given(subscriptionRepository.findTopByUser_IdOrderByStartAtDesc(1L)).willReturn(Optional.of(canceled));

        LocalDateTime before = LocalDateTime.now();
        service.subscribe(1L, subscribeReq("BASIC"));

        // 해지된 구독의 잔여기간을 얹어주면 환불받고도 기간을 챙기는 셈이 된다
        assertThat(captureSaved().getStartAt()).isBetween(before, LocalDateTime.now());
    }

    // ===== 플랜 변경 =====

    private MembershipSubscription subscriptionOnPlan(MembershipPlan current) {
        return subscriptionOnPlan(current, LocalDateTime.now().plusDays(15));
    }

    /** endAt 을 지정할 수 있는 변형(무기한 구독 = endAt null) */
    private MembershipSubscription subscriptionOnPlan(MembershipPlan current, LocalDateTime endAt) {
        User user = User.reference(1L);
        MembershipSubscription s = MembershipSubscription.createSubscription(
                user, current, LocalDateTime.now(), endAt);
        s.scheduleNextBillingAt(LocalDateTime.now().plusDays(15));
        return s;
    }

    private MembershipPlanChangeRequestDto changeReq(String newPlanCode) {
        MembershipPlanChangeRequestDto req = new MembershipPlanChangeRequestDto();
        req.setNewPlanCode(newPlanCode);
        return req;
    }

    @Test
    @DisplayName("플랜 변경 - 유효한 구독이 없으면 400")
    void changePlanWithoutSubscriptionIsRejected() {
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePlan(1L, changeReq("PREMIUM")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("유효한 구독이 없습니다");
    }

    @Test
    @DisplayName("플랜 변경 - 존재하지 않는 플랜이면 400")
    void changePlanToUnknownPlanIsRejected() {
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.of(subscriptionOnPlan(plan("BASIC", 9900L, 1))));
        given(planRepository.findByCode("NOPE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePlan(1L, changeReq("NOPE")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("플랜이 존재하지 않습니다");
    }

    @Test
    @DisplayName("플랜 변경 - 현재와 같은 플랜이면 400 (차액 0원 결제/무의미한 전환 방지)")
    void changePlanToSamePlanIsRejected() {
        MembershipPlan basic = plan("BASIC", 9900L, 1);
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.of(subscriptionOnPlan(basic)));
        given(planRepository.findByCode("BASIC")).willReturn(Optional.of(basic));

        assertThatThrownBy(() -> service.changePlan(1L, changeReq("BASIC")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("현재와 같은 플랜입니다");
    }

    /**
     * 이 API 에는 차액을 실제로 청구하는 경로가 없다. 예전에는 여기서 플랜을 즉시 올려줘서
     * change-plan 을 직접 호출하면 결제 없이 업그레이드되는 뒷문이 있었다.
     */
    @Test
    @DisplayName("업그레이드 - change-plan 으로는 거절하고 차액 결제 API 로 보낸다")
    void upgradeThroughChangePlanIsRejected() {
        MembershipPlan basic = plan("BASIC", 9900L, 1);
        MembershipPlan premium = plan("PREMIUM", 19900L, 1);
        MembershipSubscription sub = subscriptionOnPlan(basic);
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.of(sub));
        given(planRepository.findByCode("PREMIUM")).willReturn(Optional.of(premium));

        assertThatThrownBy(() -> service.changePlan(1L, changeReq("PREMIUM")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("차액 결제 API");
        // 거절했으면 플랜은 그대로여야 한다 - 무료 업그레이드가 되면 안 된다
        assertThat(sub.getMembershipPlan()).isSameAs(basic);
        assertThat(sub.getNextPlan()).isNull();
    }

    @Test
    @DisplayName("다운그레이드 - 즉시 적용하지 않고 다음 결제일로 예약하며 차액을 걷지 않는다")
    void downgradeIsScheduledForNextBilling() {
        MembershipPlan premium = plan("PREMIUM", 19900L, 1);
        MembershipPlan basic = plan("BASIC", 9900L, 1);
        MembershipSubscription sub = subscriptionOnPlan(premium);
        LocalDateTime nextBilling = sub.getNextBillingAt();
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.of(sub));
        given(planRepository.findByCode("BASIC")).willReturn(Optional.of(basic));

        MembershipPlanChangeResponseDto res = service.changePlan(1L, changeReq("BASIC"));

        assertThat(res.getChangeType()).isEqualTo(PlanChangeType.DOWNGRADE);
        // 핵심: 이미 결제한 기간에는 상위 플랜 혜택이 유지돼야 한다
        assertThat(sub.getMembershipPlan()).isSameAs(premium);
        assertThat(sub.getNextPlan()).isSameAs(basic);
        assertThat(sub.getPlanChangeScheduledAt()).isEqualTo(nextBilling);
        assertThat(res.getProrationAmount()).isNull();
    }

    @Test
    @DisplayName("업그레이드 - 종료일이 없는 구독이어도 500 이 아니라 400 으로 거절한다")
    void upgradeOnOpenEndedSubscriptionIsRejected() {
        MembershipPlan basic = plan("BASIC", 9900L, 1);
        MembershipPlan premium = plan("PREMIUM", 19900L, 1);
        // 무기한: 조회 쿼리가 "s.endAt is null" 을 유효 구독으로 취급한다
        MembershipSubscription sub = subscriptionOnPlan(basic, null);
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.of(sub));
        given(planRepository.findByCode("PREMIUM")).willReturn(Optional.of(premium));

        assertThatThrownBy(() -> service.changePlan(1L, changeReq("PREMIUM")))
                .isInstanceOf(ResponseStatusException.class);
        // 거절했으면 플랜은 그대로여야 한다
        assertThat(sub.getMembershipPlan()).isSameAs(basic);
    }

    // ===== 플랜 변경 예약 취소 =====

    @Test
    @DisplayName("예약 취소 - 유효한 구독이 없으면 400")
    void cancelScheduledPlanChangeWithoutSubscriptionIsRejected() {
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelScheduledPlanChange(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("유효한 구독이 없습니다");
    }

    @Test
    @DisplayName("예약 취소 - 예약된 플랜 변경이 없으면 400")
    void cancelScheduledPlanChangeWithoutScheduleIsRejected() {
        MembershipSubscription sub = subscriptionOnPlan(plan("BASIC", 9900L, 1)); // 예약 없음(팩토리 기본값)
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.of(sub));

        assertThatThrownBy(() -> service.cancelScheduledPlanChange(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("예약된 플랜 변경이 없습니다");
    }

    @Test
    @DisplayName("예약 취소 - 예약 정보 3개를 모두 지운다(잔여 예약으로 배치가 오작동하지 않도록)")
    void cancelScheduledPlanChangeClearsSchedule() {
        MembershipSubscription sub = subscriptionOnPlan(plan("PREMIUM", 19900L, 1));
        sub.schedulePlanChange(plan("BASIC", 9900L, 1), LocalDateTime.now().plusDays(15), PlanChangeType.DOWNGRADE);
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.of(sub));

        service.cancelScheduledPlanChange(1L);

        assertThat(sub.getNextPlan()).isNull();
        assertThat(sub.getPlanChangeScheduledAt()).isNull();
        assertThat(sub.getChangeType()).isNull();
    }
}
