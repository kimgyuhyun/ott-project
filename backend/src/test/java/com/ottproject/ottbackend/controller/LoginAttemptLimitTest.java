package com.ottproject.ottbackend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.ottproject.ottbackend.dto.AuthLoginRequestDto;
import com.ottproject.ottbackend.repository.JpaSliceTestSupport;
import com.ottproject.ottbackend.security.UserSessionRegistry;
import com.ottproject.ottbackend.service.AuthEventService;
import com.ottproject.ottbackend.service.EmailAuthService;
import com.ottproject.ottbackend.service.LoginAttemptService;
import com.ottproject.ottbackend.service.TurnstileVerifier;
import com.ottproject.ottbackend.service.VerificationEmailService;
import com.ottproject.ottbackend.util.SecurityUtil;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 로그인 시도 제한의 실제 동작 검증 (실제 Redis, 실제 스레드 100개)
 *
 * 왜 이 테스트가 필요한가
 * - 계정 잠금은 "비밀번호 비교는 계정당 5번까지"를 약속한다. 그런데 잠금 확인은 읽기만 하고 실패는 비교가
 *   끝난 뒤에 세면, 동시에 들어온 요청은 모두 잠금 확인을 통과해 비교까지 간다. 비교(BCrypt)에 드는 시간이
 *   그대로 경합 창이 된다.
 * - 실패 카운터의 만료를 증가와 따로 설정하면, 그 사이의 실패 하나로 만료 없는 카운터가 남는다.
 *   잠금 확인이 로그인보다 먼저라 그 계정은 로그인 성공으로도 풀리지 않는다.
 * - 둘 다 여러 요청이 Redis 에 명령을 보내는 순서에서 생기는 결함이라 목만으로는 재현되지 않는다.
 *
 * 테스트 환경 선택 근거(ARCHITECTURE 15절)
 * - 결함이 컨트롤러의 로그인 흐름과 LoginAttemptService-Redis 경계에 걸쳐 있다. Redis 슬라이스에 서비스 실물을
 *   넣고, 컨트롤러는 그 실물과 목(비밀번호 확인·사람 확인·감사 로그)으로 직접 조립해 login 을 부른다.
 * - 비밀번호 확인(EmailAuthService.login)은 목이다. BCrypt 시간을 BCRYPT_MILLIS 로 흉내 내고 401 로 끝난다.
 * - 사람 확인(Turnstile)은 항상 통과로 둔다. 풀이 서비스를 쓰는 공격자라도 시도 상한이 마지막 방어선이어야 한다.
 * - StringRedisTemplate 은 실물을 감싼 스파이다. 만료 설정만 실패하는 상황을 흉내 낼 때만 스텁을 건다.
 * - JpaSliceTestSupport 는 메인 클래스의 @MapperScan 때문에 가져온다(VerificationCodeAttemptLimitTest 와 같다).
 */
@DataRedisTest
@Import({JpaSliceTestSupport.class, LoginAttemptService.class})
@Testcontainers(disabledWithoutDocker = true)
@Tag("testcontainers") // testFast 가 제외하는 태그. 컨테이너를 띄우는 값이 비싸서 편집 직후 되먹임용 실행에서는 뺀다.
class LoginAttemptLimitTest {

    @Container
    @SuppressWarnings("resource") // 컨테이너 수명은 Testcontainers 가 관리한다
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private static final String EMAIL = "victim@example.com";
    private static final String COUNTER_KEY = "ott:login-fail:v1:" + EMAIL;
    private static final String WRONG_PASSWORD = "wrong-password";
    private static final int THREADS = 100;
    private static final int MAX_ATTEMPTS = 5; // app.login.max-fail-attempts 기본값
    private static final long BCRYPT_MILLIS = 200; // 비밀번호 비교 시간. 이 동안이 경합 창이다

    @Autowired
    private LoginAttemptService loginAttemptService;

    @MockitoSpyBean
    private StringRedisTemplate redisTemplate;

    private final EmailAuthService emailAuthService = mock(EmailAuthService.class);
    private final TurnstileVerifier turnstileVerifier = mock(TurnstileVerifier.class);
    private final AtomicInteger passwordChecks = new AtomicInteger();
    private EmailAuthController controller;

    @BeforeEach
    void setUp() {
        // 컨테이너를 클래스 단위로 공유하므로 앞 테스트가 남긴 카운터를 지운다
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        controller = new EmailAuthController(
                emailAuthService,
                mock(VerificationEmailService.class),
                mock(AuthEventService.class),
                loginAttemptService,
                turnstileVerifier,
                mock(UserSessionRegistry.class),
                mock(SecurityUtil.class));
        given(turnstileVerifier.verify(any())).willReturn(true);
        given(emailAuthService.login(EMAIL, WRONG_PASSWORD)).willAnswer(invocation -> {
            passwordChecks.incrementAndGet();
            Thread.sleep(BCRYPT_MILLIS);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        });
    }

    private void loginWithWrongPassword() {
        AuthLoginRequestDto request = new AuthLoginRequestDto();
        request.setEmail(EMAIL);
        request.setPassword(WRONG_PASSWORD);
        request.setTurnstileToken("solved-token");
        controller.login(request, new MockHttpSession(), new MockHttpServletRequest(), new MockHttpServletResponse());
    }

    @Test
    @DisplayName("비밀번호가 틀린 로그인 100건이 동시에 와도 비밀번호 비교는 잠금 상한(5회)까지만 일어난다")
    void concurrentWrongPasswordsStopAtTheLock() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS); // THREADS 와 같아야 ready 가 끝난다
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger wrongPassword = new AtomicInteger(); // 비밀번호가 틀려 401
        AtomicInteger locked = new AtomicInteger(); // 잠금으로 429
        AtomicInteger unexpected = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await(); // 동시에 출발
                    loginWithWrongPassword();
                    unexpected.incrementAndGet(); // 틀린 비밀번호로 성공하면 안 된다
                } catch (ResponseStatusException e) {
                    int status = e.getStatusCode().value();
                    if (status == 401) {
                        wrongPassword.incrementAndGet();
                    } else if (status == 429) {
                        locked.incrementAndGet();
                    } else {
                        unexpected.incrementAndGet();
                    }
                } catch (Exception e) {
                    unexpected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("준비되지 않은 스레드가 있다");
        start.countDown();
        if (!done.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("끝나지 않은 스레드가 있다");
        pool.shutdown();

        // 비교 전에 원자적으로 센 순번이 5 이하인 요청만 비교까지 가고, 나머지는 비교 없이 잠금으로 끝난다
        assertThat(List.of(passwordChecks.get(), wrongPassword.get(), locked.get(), unexpected.get()))
                .as("[비밀번호 비교, 비밀번호 틀림(401), 잠금(429), 예상 못 한 결과]")
                .containsExactly(MAX_ATTEMPTS, MAX_ATTEMPTS, THREADS - MAX_ATTEMPTS, 0);
    }

    @Test
    @DisplayName("만료를 따로 설정하지 않으므로, 그 호출이 실패할 상황에서도 카운터는 잠금 시간(15분) 안에 만료된다")
    void counterExpiresEvenWhenSeparateExpireWouldFail() {
        // 증가와 만료를 따로 부르던 시절에는 이 실패로 만료 없는 카운터가 남아 계정이 영구히 잠겼다
        willThrow(new RedisConnectionFailureException("만료 설정만 실패한 상황"))
                .given(redisTemplate)
                .expire(anyString(), any(Duration.class));

        assertThatThrownBy(this::loginWithWrongPassword)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(401));

        assertThat(redisTemplate.getExpire(COUNTER_KEY))
                .as("남은 TTL(초). 만료가 없으면 -1")
                .isBetween(1L, 900L);
    }

    @Test
    @DisplayName("두 번째 시도부터는 만료를 늘리지 않는다(고정 윈도우) - 공격이 이어져도 잠금은 첫 시도 기준으로 풀린다")
    void laterAttemptsDoNotExtendTheWindow() {
        assertThatThrownBy(this::loginWithWrongPassword).isInstanceOf(ResponseStatusException.class);
        redisTemplate.expire(COUNTER_KEY, Duration.ofSeconds(100)); // 첫 시도 뒤 시간이 흘러 100초 남았다고 둔다

        assertThatThrownBy(this::loginWithWrongPassword).isInstanceOf(ResponseStatusException.class);

        assertThat(redisTemplate.getExpire(COUNTER_KEY))
                .as("남은 TTL(초). 900 근처로 다시 늘었다면 시도마다 만료가 밀리는 것이다")
                .isBetween(1L, 100L);
    }
}
