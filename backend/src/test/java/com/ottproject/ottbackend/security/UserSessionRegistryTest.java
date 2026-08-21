package com.ottproject.ottbackend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

/**
 * UserSessionRegistry 단위 테스트
 *
 * 여기서 고정하는 규칙
 * - 로그인 시 세션 ID 가 사용자 인덱스에 쌓이고, 인덱스 TTL 이 세션 만료와 같은 24시간으로 갱신된다.
 * - 탈퇴 시 다른 기기 세션은 삭제되고 현재 세션은 남는다(호출부가 invalidate 로 끊으므로).
 * - Redis 가 실패해도 로그인/탈퇴 흐름을 예외로 끊지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class UserSessionRegistryTest {

    private static final Long USER_ID = 42L;
    private static final String KEY = "ott:user-sessions:v1:42";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOps;

    @Mock
    private SessionRepository<Session> sessionRepository;

    private UserSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new UserSessionRegistry(redisTemplate, sessionRepository);
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("세션 ID 를 사용자 인덱스에 넣고 TTL 을 세션 만료와 같은 24시간으로 갱신한다")
        void addsSessionIdAndRefreshesTtl() {
            given(redisTemplate.opsForSet()).willReturn(setOps);

            registry.register(USER_ID, "session-1");

            verify(setOps).add(KEY, "session-1");
            verify(redisTemplate).expire(KEY, Duration.ofDays(1));
        }

        @Test
        @DisplayName("userId 나 sessionId 가 없으면 Redis 를 건드리지 않는다")
        void ignoresMissingIdentifiers() {
            registry.register(null, "session-1");
            registry.register(USER_ID, null);
            registry.register(USER_ID, "  ");

            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("Redis 가 실패해도 예외를 던지지 않는다 — 인덱스 때문에 로그인이 막히면 안 된다")
        void swallowsRedisFailure() {
            given(redisTemplate.opsForSet()).willReturn(setOps);
            willThrow(new RuntimeException("redis down")).given(setOps).add(anyString(), anyString());

            assertThatCode(() -> registry.register(USER_ID, "session-1")).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("revokeOthers")
    class RevokeOthers {

        @Test
        @DisplayName("현재 세션을 뺀 나머지 세션을 삭제하고 인덱스를 버린다")
        void deletesEveryOtherSession() {
            given(redisTemplate.opsForSet()).willReturn(setOps);
            given(setOps.members(KEY)).willReturn(Set.of("current", "phone", "tablet"));

            registry.revokeOthers(USER_ID, "current");

            // 다른 기기 세션은 끊는다 — 이 검증이 곧 다기기 탈퇴 결함의 재현이자 회귀 방지다
            verify(sessionRepository).deleteById("phone");
            verify(sessionRepository).deleteById("tablet");
            // 현재 세션은 호출부(withdraw)가 invalidate 로 끊으므로 여기서 건드리지 않는다
            verify(sessionRepository, never()).deleteById("current");
            verify(redisTemplate).delete(KEY);
        }

        @Test
        @DisplayName("인덱스가 비어 있어도 인덱스 키는 정리한다")
        void clearsIndexWhenEmpty() {
            given(redisTemplate.opsForSet()).willReturn(setOps);
            given(setOps.members(KEY)).willReturn(Set.of());

            registry.revokeOthers(USER_ID, "current");

            verify(sessionRepository, never()).deleteById(any());
            verify(redisTemplate).delete(KEY);
        }

        @Test
        @DisplayName("userId 가 없으면 아무것도 하지 않는다")
        void ignoresMissingUserId() {
            registry.revokeOthers(null, "current");

            verifyNoInteractions(redisTemplate);
            verifyNoInteractions(sessionRepository);
        }

        @Test
        @DisplayName("Redis 가 실패해도 예외를 던지지 않는다 — 이미 커밋된 탈퇴가 실패로 보이면 안 된다")
        void swallowsRedisFailure() {
            given(redisTemplate.opsForSet()).willReturn(setOps);
            willThrow(new RuntimeException("redis down")).given(setOps).members(eq(KEY));

            assertThatCode(() -> registry.revokeOthers(USER_ID, "current")).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("빈 주입")
    class Wiring {

        /**
         * SessionRepository 는 구현체마다 타입 인자가 다르다(운영은 RedisSessionRepository = SessionRepository&lt;RedisSession&gt;).
         * 와일드카드로 선언한 의존성이 실제로 해석되지 않으면 애플리케이션이 기동조차 못 하는데,
         * 목(mock)만 쓰는 단위 테스트로는 그 실패를 잡을 수 없어 컨텍스트로 확인한다.
         * MapSessionRepository 도 SessionRepository&lt;MapSession&gt; 이라 주입 모양이 운영과 같다.
         */
        @Test
        @DisplayName("타입 인자가 다른 SessionRepository 구현체로도 주입이 해석된다")
        void resolvesSessionRepositoryWithDifferentTypeArgument() {
            new ApplicationContextRunner()
                    .withBean(StringRedisTemplate.class, () -> redisTemplate)
                    .withBean(MapSessionRepository.class, () -> new MapSessionRepository(new ConcurrentHashMap<>()))
                    .withBean(UserSessionRegistry.class)
                    .run(context -> assertThat(context).hasSingleBean(UserSessionRegistry.class));
        }
    }
}
