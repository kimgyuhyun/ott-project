package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.MembershipCancelMembershipRequestDto;
import com.ottproject.ottbackend.entity.IdempotencyKey;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.repository.IdempotencyKeyRepository;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MembershipCommandService service;

    private MembershipSubscription sub;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);
        sub = new MembershipSubscription();
        sub.setUser(user);
        sub.setStatus(MembershipSubscriptionStatus.ACTIVE);
        sub.setAutoRenew(true);
        sub.setCancelAtPeriodEnd(false);
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
    @DisplayName("해지 - 멱등키가 있으면 처리 후 키를 저장해 재요청을 막는다")
    void cancelStoresIdempotencyKey() {
        given(idempotencyKeyRepository.findByKeyValue("key-1")).willReturn(Optional.empty());
        given(subscriptionRepository.findActiveEffectiveByUser(eq(1L), eq(MembershipSubscriptionStatus.ACTIVE), any()))
                .willReturn(Optional.of(sub));

        service.cancel(1L, cancelReq("key-1"));

        verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
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

    // ===== 재개 =====

    @Test
    @DisplayName("재개 - 해지 예약된 구독의 자동갱신을 되살린다")
    void resumeRestoresAutoRenew() {
        sub.setAutoRenew(false);
        sub.setCancelAtPeriodEnd(true); // 해지 예약 상태
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
        sub.setCancelAtPeriodEnd(false); // 예약된 적 없음
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
}
