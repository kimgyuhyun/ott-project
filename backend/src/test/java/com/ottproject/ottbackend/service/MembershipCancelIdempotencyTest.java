package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.MembershipCancelMembershipRequestDto;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.exception.DuplicateIdempotentRequestException;
import com.ottproject.ottbackend.repository.IdempotencyKeyRepository;
import com.ottproject.ottbackend.repository.JpaSliceTestSupport;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * 구독 해지 멱등성의 실제 동작 검증 (실제 PostgreSQL, 실제 커밋, 실제 스레드 2개)
 *
 * 왜 이 테스트가 필요한가
 * - 예전 cancel() 은 멱등키를 findByKeyValue 로 조회해 분기하고(select-then-insert), 처리가 다 끝난
 *   맨 끝에서야 키를 저장했다. 확인과 저장 사이가 벌어져 있어 동시 요청 둘 다 조회를 통과했다.
 *   둘 다 해지 처리를 하고 해지 안내 메일을 각각 보낸 다음, 뒤늦게 한쪽이 커밋 시점의 제약 위반으로
 *   500 을 받고 롤백됐다. 메일은 트랜잭션을 따르지 않으므로 사용자에게는 안내가 두 번 도착했다.
 * - MembershipCommandServiceTest 는 목이 DataIntegrityViolationException 을 던지도록 흉내낼 뿐이다.
 *   DB 가 실제로 그 예외를 만들어 주는지, 그리고 두 번째 요청이 첫 요청의 커밋까지 실제로 대기하는지는
 *   목으로 증명할 수 없다. idempotency_keys.key_value 의 유니크 제약이 빠지면 목 기반 테스트는
 *   전부 통과하는 채로 운영에서 중복 처리가 난다(ARCHITECTURE 15절).
 *
 * 테스트 환경에 대한 메모(ARCHITECTURE 15절과의 차이)
 * - 15절은 동시성/멱등 재현을 @SpringBootTest + Testcontainers 로 하라고 한다. 이 프로젝트에는 아직
 *   @SpringBootTest 가 하나도 없어서, 전체 컨텍스트를 띄우면 Redis/Kafka/RabbitMQ/메일까지 함께 붙는다.
 *   결함은 서비스-DB 경계에 있으므로 그 부담을 지는 대신, 같은 이유로 슬라이스를 쓰는 기존
 *   PaymentMerchantUidUniqueTest / MembershipSubscriptionLockTest 와 같은 구성을 따랐다.
 *   핵심 요구(실제 커밋, 실제 스레드 2개, 테스트 메서드에 @Transactional 없음)는 그대로 지킨다.
 * - 감싸는 트랜잭션을 끄는 이유도 같다. 서비스가 자기 @Transactional 로 진짜 커밋을 해야 경합이 재현된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 컨테이너 URL 을 쓰기 위해 자동 대체를 끈다
@Import({JpaSliceTestSupport.class, MembershipCommandService.class})
@Testcontainers(disabledWithoutDocker = true)
@Tag("testcontainers") // testFast 가 제외하는 태그. 이 클래스들이 전체 실행 9분 중 8분 40초를 쓴다.
@TestPropertySource(properties = {
        // 엔티티 기준으로 스키마를 만든다. IdempotencyKey.keyValue 의 unique 선언이 실제 인덱스가 되는지도 함께 검증된다.
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create", // create-drop 이 아니다: 종료 시 drop DDL 이 이미 내려간 컨테이너에 붙으려다 30초를 버린다
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED) // @DataJpaTest 의 감싸는 트랜잭션을 끈다
class MembershipCancelIdempotencyTest {

    @Container
    @SuppressWarnings("resource") // 컨테이너 수명은 Testcontainers 가 관리한다
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    /** 같은 해지 요청이 두 번 도착했을 때 두 요청이 들고 오는 멱등키 */
    private static final String CANCEL_KEY = "cancel-key-1";

    @Autowired
    private MembershipCommandService service;

    @Autowired
    private MembershipSubscriptionRepository subscriptionRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipPlanRepository planRepository;

    // 메일 발송은 이 테스트의 관심사가 아니지만, "몇 번 불렸는가"가 결함의 사용자 가시적 증상이다.
    @MockitoBean
    private MembershipNotificationService notificationService;

    private Long userId;

    @BeforeEach
    void setUp() {
        idempotencyKeyRepository.deleteAll();
        subscriptionRepository.deleteAll();
        planRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(User.createLocalUser("cancel@example.com", "encoded", "테스터"));
        MembershipPlan plan = planRepository.save(
                MembershipPlan.createBasicPlan("Basic", "설명", new Money(9900L, "KRW"), 1));
        userId = user.getId();

        LocalDateTime now = LocalDateTime.now();
        subscriptionRepository.save(MembershipSubscription.createSubscription(
                user, plan, now.minusDays(5), now.plusDays(25))); // ACTIVE + 잔여기간
    }

    private MembershipCancelMembershipRequestDto cancelReq(String key) {
        MembershipCancelMembershipRequestDto req = new MembershipCancelMembershipRequestDto();
        req.idempotencyKey = key;
        return req;
    }

    private Throwable resultOrNullIfStillRunning(Future<Throwable> future, long seconds) throws Exception {
        try {
            return future.get(seconds, TimeUnit.SECONDS);
        } catch (TimeoutException stillBlocked) {
            return null;
        }
    }

    /**
     * 이 테스트가 이 파일의 존재 이유다.
     * 멱등 판정을 조회 후 분기로 되돌리거나 idempotency_keys 의 유니크 제약을 지우면 여기서 잡힌다.
     */
    @Test
    @DisplayName("같은 멱등키로 동시에 해지 요청이 오면 한 번만 처리된다 - 중복 해지 안내 메일 차단")
    void concurrentCancelWithSameKeyIsProcessedOnce() throws Exception {
        CountDownLatch firstReachedNotification = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger notificationCalls = new AtomicInteger();

        // 첫 요청을 메일 발송 지점에 붙잡아 둔다. 이 지점은 멱등키를 선삽입한 뒤, 아직 커밋하기 전이다.
        doAnswer(invocation -> {
            if (notificationCalls.incrementAndGet() == 1) {
                firstReachedNotification.countDown();
                releaseFirst.await(10, TimeUnit.SECONDS);
            }
            return null;
        }).when(notificationService).sendCancelAtPeriodEnd(any(), any());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = pool.submit(() -> catchThrowable(() -> service.cancel(userId, cancelReq(CANCEL_KEY))));
            assertThat(firstReachedNotification.await(30, TimeUnit.SECONDS)).isTrue(); // 첫 요청이 키를 선점하고 커밋 직전까지 옴

            // 두 번째 요청. 별도 스레드여야 별도 커넥션/트랜잭션이 열린다.
            Future<Throwable> second = pool.submit(() -> catchThrowable(() -> service.cancel(userId, cancelReq(CANCEL_KEY))));

            // 첫 요청이 커밋하기 전에는 유니크 인덱스에서 대기해야 한다.
            // 여기서 이미 끝나 있으면 제약이 판정하지 않았다는 뜻이다 = 둘 다 통과했다.
            assertThat(resultOrNullIfStillRunning(second, 3)).isNull();

            releaseFirst.countDown(); // 첫 요청 커밋

            assertThat(first.get(30, TimeUnit.SECONDS)).isNull(); // 첫 요청은 정상 완료
            assertThat(second.get(30, TimeUnit.SECONDS))
                    .isInstanceOf(DuplicateIdempotentRequestException.class); // 두 번째는 중복으로 판정돼 중단

            // 사용자에게 보이는 증상: 해지 안내 메일이 두 번 나가면 안 된다
            assertThat(notificationCalls.get()).isEqualTo(1);
            assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
        } finally {
            releaseFirst.countDown(); // 실패 경로에서도 첫 요청이 매달려 있지 않게 한다
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("같은 멱등키로 순차 재요청하면 두 번째는 조용히 무시된다 - 예외 없이 성공")
    void sequentialCancelWithSameKeyIsNoOp() {
        service.cancel(userId, cancelReq(CANCEL_KEY));
        service.cancel(userId, cancelReq(CANCEL_KEY)); // 이미 커밋된 키라 빠른 경로에서 걸러진다

        assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
        assertThat(subscriptionRepository.findAll())
                .singleElement()
                .satisfies(sub -> {
                    assertThat(sub.isAutoRenew()).isFalse();
                    assertThat(sub.isCancelAtPeriodEnd()).isTrue();
                });
    }

    @Test
    @DisplayName("서로 다른 멱등키는 막히지 않는다 - 유니크 제약이 정상 요청을 잡아먹지 않아야 한다")
    void differentKeysAreNotBlocked() {
        service.cancel(userId, cancelReq("cancel-key-a"));
        service.cancel(userId, cancelReq("cancel-key-b")); // 해지 상태는 이미 반영됐지만 키는 따로 남는다

        assertThat(idempotencyKeyRepository.count()).isEqualTo(2);
    }
}
