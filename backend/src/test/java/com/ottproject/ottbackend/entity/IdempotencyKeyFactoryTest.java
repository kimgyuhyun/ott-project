package com.ottproject.ottbackend.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * IdempotencyKey 정적 팩토리 검증
 *
 * 회귀 배경(5fe9002)
 * - 과거 이 팩토리는 response 를 필수값으로 검증했는데, 엔티티에 응답 컬럼 자체가 없어 저장되지도 않는 값이었다.
 *   모든 호출처가 null/"" 를 넘기던 탓에 웹훅 처리마다 IllegalArgumentException 으로 멱등키 저장이 실패했고,
 *   PG 가 같은 이벤트를 계속 재전송했다.
 * - 그래서 "response 를 검증하지 않는다" 는 것이 이 팩토리의 규칙이며, 여기서 고정한다.
 */
class IdempotencyKeyFactoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 12, 0);

    @Test
    @DisplayName("응답값이 null 이나 빈 문자열이어도 멱등키를 만든다 - 웹훅 재전송 폭주 회귀 방지")
    void doesNotValidateResponse() {
        assertThatCode(() -> IdempotencyKey.createIdempotencyKey("evt_1", "payment.webhook", null, NOW))
                .doesNotThrowAnyException();
        assertThatCode(() -> IdempotencyKey.createIdempotencyKey("evt_2", "payment.webhook", "", NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("키와 용도의 앞뒤 공백을 제거해 저장한다 - 키는 unique 조회 기준이다")
    void trimsKeyAndPurpose() {
        IdempotencyKey key = IdempotencyKey.createIdempotencyKey("  evt_1  ", "  membership.cancel  ", null, NOW);

        assertThat(key.getKeyValue()).isEqualTo("evt_1");
        assertThat(key.getPurpose()).isEqualTo("membership.cancel");
    }

    @Test
    @DisplayName("호출자가 넘긴 생성 시각을 그대로 남긴다 - 엔티티는 현재 시각을 스스로 읽지 않는다")
    void recordsCreatedAt() {
        IdempotencyKey key = IdempotencyKey.createIdempotencyKey("evt_1", "payment.webhook", null, NOW);

        assertThat(key.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("키가 없거나 공백뿐이면 거부한다 - 멱등성 보장이 불가능하다")
    void rejectsMissingKey() {
        assertThatThrownBy(() -> IdempotencyKey.createIdempotencyKey(null, "payment.webhook", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyKey.createIdempotencyKey("   ", "payment.webhook", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("용도가 없거나 공백뿐이면 거부한다")
    void rejectsMissingPurpose() {
        assertThatThrownBy(() -> IdempotencyKey.createIdempotencyKey("evt_1", null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyKey.createIdempotencyKey("evt_1", "   ", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
