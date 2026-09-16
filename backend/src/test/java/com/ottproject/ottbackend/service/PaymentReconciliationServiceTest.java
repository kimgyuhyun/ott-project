package com.ottproject.ottbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ottproject.ottbackend.entity.IdempotencyKey;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.enums.IdempotencyKeyStatus;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.repository.IdempotencyKeyRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * PaymentReconciliationService 판정 불가 카운터 단위 테스트
 *
 * 여기서 고정하는 규칙(ARCHITECTURE 5절, PLATFORM 9절)
 * - 대사가 결론을 내지 못한 건은 경보가 보는 카운터로 올린다.
 * - 정상 미결은 세지 않는다. 경보 기준이 "정상값이 정의상 0" 이어야 하기 때문이다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentCommandService paymentCommandService;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private PaymentReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentReconciliationService(
                paymentRepository, paymentCommandService, idempotencyKeyRepository, meterRegistry);
        service.registerCounters();
    }

    private double inconclusive(String batch) {
        return meterRegistry
                .get(PaymentReconciliationService.INCONCLUSIVE_METRIC)
                .tag("batch", batch)
                .counter()
                .count();
    }

    @Test
    @DisplayName("기동 시점에 두 배치의 카운터가 0 으로 존재한다 — 첫 증가를 increase() 가 놓치지 않게")
    void registersCountersAtZero() {
        assertThat(inconclusive("pending")).isZero();
        assertThat(inconclusive("refund_claim")).isZero();
    }

    @Test
    @DisplayName("환불 선점: 정리하지 못한 건과 예외로 끝난 건을 센다")
    void countsUnresolvedRefundClaims() {
        LocalDateTime now = LocalDateTime.now();
        given(idempotencyKeyRepository.findByPurposeAndStatusAndCreatedAtBefore(
                        eq(PaymentCommandService.REFUND_KEY_PURPOSE), eq(IdempotencyKeyStatus.CLAIMED), any()))
                .willReturn(List.of(
                        IdempotencyKey.createClaimedIdempotencyKey("payment.refund:1", "payment.refund", now),
                        IdempotencyKey.createClaimedIdempotencyKey("payment.refund:2", "payment.refund", now),
                        IdempotencyKey.createClaimedIdempotencyKey("payment.refund:3", "payment.refund", now)));
        given(paymentCommandService.reconcileRefundClaim("payment.refund:1")).willReturn(true); // 정리됨
        given(paymentCommandService.reconcileRefundClaim("payment.refund:2")).willReturn(false); // 판정 불가
        given(paymentCommandService.reconcileRefundClaim("payment.refund:3"))
                .willThrow(new RuntimeException("db down")); // 예외

        service.reconcileStaleRefundClaims();

        assertThat(inconclusive("refund_claim")).isEqualTo(2.0);
        assertThat(inconclusive("pending")).isZero();
    }

    @Test
    @DisplayName("PENDING: 예외로 끝난 건만 센다 — false 에는 결제창 이탈 같은 정상 미결이 섞여 있다")
    void countsOnlyFailedPendingReconciles() {
        Payment settled = mock(Payment.class);
        Payment stillPending = mock(Payment.class);
        Payment broken = mock(Payment.class);
        given(settled.getId()).willReturn(1L);
        given(stillPending.getId()).willReturn(2L);
        given(broken.getId()).willReturn(3L);
        given(paymentRepository.findByStatusAndCreatedAtBetween(eq(PaymentStatus.PENDING), any(), any()))
                .willReturn(List.of(settled, stillPending, broken));
        given(paymentCommandService.reconcilePending(1L)).willReturn(true);
        given(paymentCommandService.reconcilePending(2L)).willReturn(false);
        given(paymentCommandService.reconcilePending(3L)).willThrow(new RuntimeException("db down"));

        service.reconcilePendingPayments();

        assertThat(inconclusive("pending")).isEqualTo(1.0);
        assertThat(inconclusive("refund_claim")).isZero();
    }
}
