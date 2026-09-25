package com.ottproject.ottbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

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
 * - 상한은 비교하기 "전에" 세야 지켜진다. 비교한 뒤에 세면 동시에 들어온 요청이 모두 비교까지 가서
 *   상한이 동시 요청 수만큼 늘어난다. 목은 호출 순서만 보여줄 뿐이고, 동시 요청 중 몇 건이 실제로
 *   비교에 이르는지는 Redis 가 명령을 실제로 처리하는 환경에서만 확인된다.
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

    /**
     * 현재 코드의 동작을 기록한다: 시도 상한이 없어 모든 시도가 코드와 비교된다(이 테스트는 초록).
     *
     * 방어가 들어가면 뒤집혀야 하는 단언
     * - 코드를 읽어 비교한 요청이 100건이다 → 5건이어야 한다
     * - 틀림 응답이 100건이다 → 5건이고, 나머지 95건은 횟수 초과로 거부돼야 한다
     * - 100번 틀린 뒤에도 정답 코드가 통한다 → 거부돼야 한다(코드 폐기)
     */
    @Test
    @DisplayName("틀린 코드 100건이 동시에 오면 100건 모두 코드와 비교되고, 그 뒤에도 정답 코드가 통한다(현재 결함)")
    void concurrentWrongGuessesAreAllCompared() throws InterruptedException {
        String code = sendCode();
        String wrong = wrongCodeFor(code);
        resetCommandStats(); // 발송이 남긴 명령은 빼고, 아래 동시 구간의 명령만 센다

        ExecutorService pool = Executors.newFixedThreadPool(THREADS); // THREADS 와 같아야 ready 가 끝난다
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger mismatched = new AtomicInteger(); // "틀림"으로 응답한 요청(400)
        AtomicInteger unexpected = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await(); // 동시에 출발
                    if (!service.verifyCode(EMAIL, wrong)) {
                        mismatched.incrementAndGet();
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

        // 결함: 몇 번을 틀려도 코드가 살아 있고, 동시에 온 시도가 전부 비교된다
        assertThat(commandCalls("get")).as("코드를 읽어 비교한 요청 수").isEqualTo(THREADS);
        assertThat(List.of(mismatched.get(), unexpected.get()))
                .as("[틀림으로 응답한 요청, 예상 못 한 예외]")
                .containsExactly(THREADS, 0);
        assertThat(service.verifyCode(EMAIL, code)).as("100번 틀린 뒤의 정답 코드").isTrue();
    }
}
