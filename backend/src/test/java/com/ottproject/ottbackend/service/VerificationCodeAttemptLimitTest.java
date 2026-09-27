package com.ottproject.ottbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.ottproject.ottbackend.exception.VerificationAttemptsExceededException;
import com.ottproject.ottbackend.repository.JpaSliceTestSupport;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 인증 코드 입력 시도 상한의 실제 동작 검증 (실제 Redis, 실제 스레드 100개)
 *
 * 왜 이 테스트가 필요한가
 * - 6자리 코드는 100만 가지다. 틀려도 코드가 살아 있으면 공격자는 코드가 만료되는 10분 동안 계속 찍을 수 있다.
 *   그래서 코드 하나에 입력 5회까지만 비교하고, 넘기면 코드를 폐기한다.
 * - 상한은 비교하기 "전에" 세야 지켜진다. 비교한 뒤에 세면 동시에 들어온 요청이 모두 비교까지 가서
 *   상한이 동시 요청 수만큼 늘어난다. 목은 호출 순서만 보여줄 뿐이고, 동시 요청 100건 중 정확히 5건만
 *   비교에 이른다는 것은 Redis 가 INCR 을 원자적으로 처리하는 실제 환경에서만 확인된다.
 *
 * 테스트 환경 선택 근거(ARCHITECTURE 15절)
 * - 결함이 서비스-Redis 경계에 있으므로 Redis 슬라이스(@DataRedisTest)에 서비스 실물을 @Import 했다.
 *   메일 발송만 목이다. 발송은 이 결함과 무관하고, 실제로 저장된 코드를 메일 본문에서 꺼내는 데만 쓴다.
 * - JpaSliceTestSupport 는 JPA 때문이 아니라 메인 클래스의 @MapperScan 때문에 가져온다. 이 슬라이스에서도
 *   매퍼 빈을 등록하려 해서, 껍데기 SqlSessionFactory 가 없으면 컨텍스트가 뜨지 않는다.
 * - 이미지 태그는 docker-compose.yml 의 redis 서비스와 맞춘다.
 */
@DataRedisTest
@Import({JpaSliceTestSupport.class, VerificationEmailService.class})
@Testcontainers(disabledWithoutDocker = true)
@Tag("testcontainers") // testFast 가 제외하는 태그. 컨테이너를 띄우는 값이 비싸서 편집 직후 되먹임용 실행에서는 뺀다.
@TestPropertySource(properties = "spring.mail.username=noreply@test.com")
class VerificationCodeAttemptLimitTest {

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
    private static final int THREADS = 100;
    private static final int MAX_ATTEMPTS = 5;

    @Autowired
    private VerificationEmailService service;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        // 컨테이너를 클래스 단위로 공유하므로 앞 테스트가 남긴 코드·카운터를 지운다
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    /** 코드를 발송하고, 메일 본문에 담긴(= Redis 에 저장된) 코드를 돌려준다 */
    private String sendCode() {
        service.sendVerificationEmail(EMAIL);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        Matcher m = Pattern.compile("인증 코드: (\\d{6})").matcher(captor.getValue().getText());
        assertThat(m.find()).as("메일 본문에 인증 코드가 있어야 한다").isTrue();
        return m.group(1);
    }

    private static String wrongCodeFor(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    /** 서버의 명령 통계를 0으로 되돌린다(CONFIG RESETSTAT). 이 뒤에 실행된 명령만 센다 */
    private void resetCommandStats() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().resetConfigStats();
            return null;
        });
    }

    /** Redis 서버가 센 명령 실행 횟수(INFO commandstats 의 cmdstat_<명령>:calls=N,...) */
    private long commandCalls(String command) {
        Properties stats = redisTemplate.execute((RedisCallback<Properties>)
                connection -> connection.serverCommands().info("commandstats"));
        String line = stats.getProperty("cmdstat_" + command);
        if (line == null) {
            return 0; // 한 번도 실행되지 않은 명령은 통계에 나오지 않는다
        }
        return Long.parseLong(line.replaceFirst("^calls=(\\d+),.*$", "$1"));
    }

    @Test
    @DisplayName("틀린 코드 100건이 동시에 와도 비교까지 가는 것은 5건뿐이고, 그 뒤에는 정답 코드도 거부된다")
    void concurrentWrongGuessesAreCappedAndBurnTheCode() throws InterruptedException {
        String code = sendCode();
        String wrong = wrongCodeFor(code);
        resetCommandStats(); // 발송이 남긴 명령은 빼고, 아래 동시 구간의 명령만 센다

        ExecutorService pool = Executors.newFixedThreadPool(THREADS); // THREADS 와 같아야 ready 가 끝난다
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger mismatched = new AtomicInteger(); // "틀림"으로 응답한 요청(400)
        AtomicInteger rejected = new AtomicInteger(); // 횟수 초과로 거부된 요청(429)
        AtomicInteger unexpected = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await(); // 동시에 출발
                    if (!service.verifyCode(EMAIL, wrong)) {
                        mismatched.incrementAndGet();
                    }
                } catch (VerificationAttemptsExceededException e) {
                    rejected.incrementAndGet();
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

        // 비교하려면 저장된 코드를 읽어야 한다(GET). 서버가 센 GET 횟수가 곧 코드와 비교된 요청 수다.
        // 응답만 세면 "먼저 비교하고 나중에 거부하는" 구현도 5/95 로 보여 통과한다.
        assertThat(commandCalls("get")).as("코드를 읽어 비교한 요청 수").isEqualTo(MAX_ATTEMPTS);
        assertThat(List.of(mismatched.get(), rejected.get(), unexpected.get()))
                .as("[틀림으로 응답한 요청, 횟수 초과로 거부된 요청, 예상 못 한 예외]")
                .containsExactly(MAX_ATTEMPTS, THREADS - MAX_ATTEMPTS, 0);
        // 코드가 폐기됐으므로 정답을 알아도 더는 인증할 수 없다
        assertThatThrownBy(() -> service.verifyCode(EMAIL, code))
                .isInstanceOf(VerificationAttemptsExceededException.class);
        assertThat(service.isEmailVerified(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("시도를 다 쓴 뒤 새 코드를 받으면 횟수가 새로 세어져 새 코드로 인증된다")
    void newCodeStartsFreshAttempts() {
        String wrong = wrongCodeFor(sendCode());
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            assertThat(service.verifyCode(EMAIL, wrong)).isFalse();
        }
        assertThatThrownBy(() -> service.verifyCode(EMAIL, wrong))
                .isInstanceOf(VerificationAttemptsExceededException.class);

        String newCode = sendCode();

        // 정상 사용자가 오타로 시도를 다 써도 새 코드로 복구할 수 있어야 한다
        assertThat(service.verifyCode(EMAIL, newCode)).isTrue();
        assertThat(service.isEmailVerified(EMAIL)).isTrue();
    }

    @Test
    @DisplayName("코드가 없는 이메일로 시도해도 시도 카운터는 10분 TTL 을 갖는다(만료 없는 키가 쌓이지 않는다)")
    void attemptCounterAlwaysExpires() {
        // 아무 이메일로나 찔러볼 수 있으므로, TTL 이 빠지면 키가 영구히 쌓인다(이 Redis 는 maxmemory 가 없다)
        service.verifyCode(EMAIL, "000000");

        Long ttl = redisTemplate.getExpire("ott:email-verification:v1:attempts:" + EMAIL);
        assertThat(ttl).as("남은 TTL(초). 키가 없으면 -2, TTL 이 없으면 -1").isBetween(1L, 600L);
    }
}
