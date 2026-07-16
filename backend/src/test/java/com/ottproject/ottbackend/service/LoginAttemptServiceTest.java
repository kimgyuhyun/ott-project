package com.ottproject.ottbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * LoginAttemptService 단위 테스트
 *
 * 지키려는 규칙(무차별 대입 방어)
 * - 실패가 임계치(5회) 이상 쌓이면 잠금
 * - 최초 실패에만 TTL(잠금 시간)을 걸어 고정 윈도우를 유지한다
 *   (실패마다 TTL 을 갱신하면 공격자가 계속 시도해 잠금이 영원히 안 풀리거나 반대로 리셋될 수 있다)
 * - 로그인 성공 시 카운터 삭제
 * - 실패 1회부터 Turnstile(사람 확인) 요구
 * - Redis 값이 깨져 있어도 잠금/차단으로 오판하지 않는다(정상 사용자 차단 방지)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private LoginAttemptService service;

    private static final String EMAIL = "user@example.com";
    private static final String KEY = "ott:login-fail:v1:user@example.com";

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        // @Value 필드는 단위 테스트에서 주입되지 않으므로 운영 기본값과 동일하게 세팅
        ReflectionTestUtils.setField(service, "maxFailAttempts", 5);
        ReflectionTestUtils.setField(service, "lockMinutes", 15L);
        ReflectionTestUtils.setField(service, "turnstileAfterFails", 1);
    }

    // ===== 잠금 판정 =====

    @Test
    @DisplayName("실패 이력이 없으면 잠기지 않는다")
    void noFailureIsNotBlocked() {
        given(valueOps.get(KEY)).willReturn(null);

        assertThat(service.isBlocked(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("임계치 미만(4회)은 아직 잠기지 않는다")
    void belowThresholdIsNotBlocked() {
        given(valueOps.get(KEY)).willReturn("4");

        assertThat(service.isBlocked(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("임계치 도달(5회)이면 잠긴다 - 경계값")
    void atThresholdIsBlocked() {
        given(valueOps.get(KEY)).willReturn("5");

        assertThat(service.isBlocked(EMAIL)).isTrue();
    }

    @Test
    @DisplayName("임계치 초과(6회)도 잠긴다")
    void aboveThresholdIsBlocked() {
        given(valueOps.get(KEY)).willReturn("6");

        assertThat(service.isBlocked(EMAIL)).isTrue();
    }

    // ===== 실패 누적 / TTL =====

    @Test
    @DisplayName("최초 실패에만 TTL(15분)을 건다 - 고정 윈도우")
    void firstFailureSetsTtl() {
        given(valueOps.increment(KEY)).willReturn(1L);

        long count = service.recordFailure(EMAIL);

        assertThat(count).isEqualTo(1L);
        verify(redisTemplate).expire(KEY, Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("두 번째 실패부터는 TTL 을 갱신하지 않는다 - 잠금 시간이 밀리면 안 됨")
    void laterFailuresDoNotExtendTtl() {
        given(valueOps.increment(KEY)).willReturn(2L);

        service.recordFailure(EMAIL);

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("로그인 성공 시 카운터를 삭제한다")
    void resetDeletesCounter() {
        service.reset(EMAIL);

        verify(redisTemplate).delete(KEY);
    }

    // ===== Turnstile 요구 판정 =====

    @Test
    @DisplayName("실패 이력이 없으면 사람 확인을 요구하지 않는다 - 정상 첫 로그인은 마찰 없음")
    void firstLoginNeedsNoChallenge() {
        given(valueOps.get(KEY)).willReturn(null);

        assertThat(service.isChallengeRequired(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("실패 1회부터 사람 확인을 요구한다")
    void challengeRequiredAfterFirstFailure() {
        given(valueOps.get(KEY)).willReturn("1");

        assertThat(service.isChallengeRequired(EMAIL)).isTrue();
    }

    // ===== 비정상 입력 방어 =====

    @Test
    @DisplayName("Redis 값이 숫자가 아니면 잠금으로 보지 않는다 - 정상 사용자 차단 방지")
    void corruptedCounterDoesNotBlock() {
        given(valueOps.get(KEY)).willReturn("깨진값");

        assertThat(service.isBlocked(EMAIL)).isFalse();
        assertThat(service.getFailCount(EMAIL)).isZero();
    }

    @Test
    @DisplayName("이메일이 비어 있으면 Redis 를 조회하지 않고 통과시킨다")
    void blankEmailIsIgnored() {
        assertThat(service.isBlocked("")).isFalse();
        assertThat(service.isBlocked(null)).isFalse();
        assertThat(service.getFailCount(null)).isZero();
        assertThat(service.recordFailure(null)).isZero();

        verify(valueOps, never()).get(anyString());
        verify(valueOps, never()).increment(anyString());
    }

    @Test
    @DisplayName("이메일은 대소문자/공백 무시하고 같은 키를 쓴다 - 우회 방지")
    void emailIsNormalizedIntoOneKey() {
        given(valueOps.get(KEY)).willReturn("5");

        // "User@Example.com " 로 시도해도 같은 카운터에 걸려야 한다
        assertThat(service.isBlocked("  User@Example.COM  ")).isTrue();
        verify(valueOps).get(eq(KEY));
    }
}
