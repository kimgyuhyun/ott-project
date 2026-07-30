package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * findByIdForUpdate 의 실제 잠금 동작 검증 (실제 PostgreSQL)
 *
 * 왜 이 테스트가 필요한가
 * - RecurringBillingServiceTest 의 단위 테스트는 "findByIdForUpdate 를 호출하는지"만 본다.
 *   락이 실제로 동시 실행을 막는지는 목으로는 확인할 수 없다.
 * - 정기결제 재청구는 실제 돈이 나가는 경로다. MQ 가 같은 메시지를 두 번 배달했을 때
 *   두 트랜잭션이 나란히 결제 API 를 호출하지 않는다는 보장이 코드 주석이 아니라 테스트로 있어야 한다.
 *
 * 왜 H2 로는 안 되는가
 * - 행 잠금과 NOWAIT 은 DB 제품 고유 동작이다. H2 의 잠금 모델은 PostgreSQL 과 다르므로
 *   H2 에서 통과해도 운영 동작을 보장하지 못한다. (같은 이유로 AnimeRepositoryReadOnlyLockTest 도 컨테이너를 쓴다)
 *
 * 트랜잭션 구성(명시)
 * - @DataJpaTest 가 감싸는 트랜잭션을 NOT_SUPPORTED 로 끄고, TransactionTemplate 으로 트랜잭션을 직접 연다.
 *   잠금 경합을 보려면 서로 다른 커넥션의 트랜잭션 2개가 동시에 열려 있어야 하기 때문이다.
 * - 트랜잭션 롤백이 없으므로 픽스처는 각 테스트가 직접 정리한다.
 *
 * Docker 가 없으면 컨테이너를 못 띄운다. Testcontainers 가 그 경우 조건부로 테스트를 건너뛴다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 컨테이너 URL 을 쓰기 위해 자동 대체를 끈다
@Import(JpaSliceTestSupport.class)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        // Flyway 마이그레이션 대신 엔티티 기준 스키마를 만든다(이 테스트는 스키마 이력이 아니라 잠금 동작을 본다).
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED) // @DataJpaTest 의 감싸는 트랜잭션을 끈다
class MembershipSubscriptionLockTest {

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

    @Autowired
    private MembershipSubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipPlanRepository planRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long subscriptionId;

    @BeforeEach
    void setUp() {
        subscriptionRepository.deleteAll();
        planRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(User.createLocalUser("billing@example.com", "encoded", "테스터"));
        MembershipPlan plan = planRepository.save(
                MembershipPlan.createBasicPlan("Basic", "설명", new Money(9900L, "KRW"), 1));

        LocalDateTime now = LocalDateTime.now();
        MembershipSubscription sub = MembershipSubscription.createSubscription(
                user, plan, now.minusDays(30), now.plusDays(10));
        sub.recordDeclinedCharge(now, "CARD_DECLINED", "1차 청구 실패"); // → PAST_DUE, retryCount=1
        subscriptionId = subscriptionRepository.save(sub).getId();
    }

    private TransactionTemplate writeTransaction() {
        return new TransactionTemplate(transactionManager);
    }

    /**
     * 이 테스트가 이 파일의 존재 이유다. findByIdForUpdate 를 락 없는 조회로 되돌리면 여기서 잡힌다.
     */
    @Test
    @DisplayName("잠긴 구독을 다른 트랜잭션이 다시 잠그려 하면 NOWAIT 으로 즉시 실패한다 - MQ 중복 배달 시 이중 청구 차단")
    void secondTransactionCannotAcquireLockHeldByFirst() throws Exception {
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            writeTransaction().execute(status -> {
                subscriptionRepository.findByIdForUpdate(subscriptionId); // 첫 번째 배달: 락 선점

                // 두 번째 배달을 흉내낸다. 별도 스레드여야 별도 커넥션/트랜잭션이 열린다.
                Future<Throwable> second = other.submit(() -> catchThrowable(
                        () -> writeTransaction().execute(inner ->
                                subscriptionRepository.findByIdForUpdate(subscriptionId))));

                Throwable thrown;
                try {
                    // NOWAIT 이므로 즉시 돌아와야 한다. 여기서 타임아웃이 나면 대기 모드로 바뀐 것이다.
                    thrown = second.get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException("두 번째 트랜잭션이 즉시 실패하지 않았다(대기 중)", e);
                }

                assertThat(thrown).isInstanceOf(PessimisticLockingFailureException.class);
                return null;
            });
        } finally {
            other.shutdownNow();
        }
    }

    @Test
    @DisplayName("락 없는 일반 조회는 잠긴 행에도 막히지 않는다 - findById 에 @Lock 이 번지면 여기서 잡힌다")
    void plainReadIsNotBlockedByTheLock() throws Exception {
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            writeTransaction().execute(status -> {
                subscriptionRepository.findByIdForUpdate(subscriptionId); // 락 선점

                Future<Optional<MembershipSubscription>> read = other.submit(() ->
                        writeTransaction().execute(inner -> subscriptionRepository.findById(subscriptionId)));

                Optional<MembershipSubscription> found;
                try {
                    found = read.get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException("일반 조회가 잠금에 걸렸다 - findById 에 락이 번졌는지 확인할 것", e);
                }

                assertThat(found).isPresent();
                return null;
            });
        } finally {
            other.shutdownNow();
        }
    }
}
