package com.ottproject.ottbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RebillMerchantUid 단위 테스트
 *
 * 지키려는 규칙
 * - 같은 청구 시도는 항상 같은 값 (중복 배달 두 건이 유니크 제약에서 충돌해야 하므로)
 * - 시도 횟수가 다르면 다른 값 (재시도는 새 청구다)
 * - 결제수단이 다르면 다른 값 (아임포트가 실패한 시도의 uid 도 소진시켜 폴백이 죽는다)
 * - 청구주기가 다르면 다른 값 (다음 달 청구가 중복으로 차단되면 안 된다)
 * - 아임포트 merchant_uid 최대 길이 40자
 */
class RebillMerchantUidTest {

    private static final LocalDateTime ANCHOR = LocalDateTime.of(2026, 8, 8, 3, 0);

    @Test
    @DisplayName("같은 청구 시도는 항상 같은 값 - 중복 배달이 유니크 제약에 걸린다")
    void sameAttemptProducesSameValue() {
        String first = RebillMerchantUid.create(10L, ANCHOR, 1, 100L);
        String second = RebillMerchantUid.create(10L, ANCHOR, 1, 100L);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("시도 횟수/결제수단/청구주기가 다르면 다른 값")
    void differentComponentsProduceDifferentValues() {
        String base = RebillMerchantUid.create(10L, ANCHOR, 1, 100L);

        assertThat(RebillMerchantUid.create(10L, ANCHOR, 2, 100L)).isNotEqualTo(base); // 재시도
        assertThat(RebillMerchantUid.create(10L, ANCHOR, 1, 200L)).isNotEqualTo(base); // 폴백 수단
        assertThat(RebillMerchantUid.create(10L, ANCHOR.plusMonths(1), 1, 100L)).isNotEqualTo(base); // 다음 주기
        assertThat(RebillMerchantUid.create(11L, ANCHOR, 1, 100L)).isNotEqualTo(base); // 다른 구독
    }

    @Test
    @DisplayName("ID가 9자리여도 아임포트 상한 40자를 넘지 않는다")
    void staysWithinIamportLengthLimit() {
        String uid = RebillMerchantUid.create(999_999_999L, ANCHOR, 99, 999_999_999L);

        assertThat(uid.length()).isLessThanOrEqualTo(40);
    }

    @Test
    @DisplayName("주기 앵커가 없으면 거부한다 - 서로 다른 주기가 같은 값을 갖게 된다")
    void rejectsMissingCycleAnchor() {
        assertThatThrownBy(() -> RebillMerchantUid.create(10L, null, 1, 100L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("재청구 결제 식별 - 체크아웃/차액 결제와 구분된다")
    void identifiesRebillPayments() {
        assertThat(RebillMerchantUid.isRebill(RebillMerchantUid.create(10L, ANCHOR, 1, 100L)))
                .isTrue();
        assertThat(RebillMerchantUid.isRebill("order_1755600000000")).isFalse();
        assertThat(RebillMerchantUid.isRebill("proration_abc123")).isFalse();
        assertThat(RebillMerchantUid.isRebill(null)).isFalse();
    }

    @Test
    @DisplayName("구독 ID 역추출 - 대사가 어느 구독을 연장할지 찾는 유일한 연결고리")
    void extractsSubscriptionId() {
        assertThat(RebillMerchantUid.subscriptionIdOf(RebillMerchantUid.create(10L, ANCHOR, 1, 100L)))
                .isEqualTo(10L);
        assertThat(RebillMerchantUid.subscriptionIdOf("order_1755600000000")).isNull();
        assertThat(RebillMerchantUid.subscriptionIdOf("rebill_broken")).isNull();
        assertThat(RebillMerchantUid.subscriptionIdOf(null)).isNull();
    }
}
