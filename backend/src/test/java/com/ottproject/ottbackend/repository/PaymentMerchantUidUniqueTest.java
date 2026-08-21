package com.ottproject.ottbackend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.PaymentProvider;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * merchant_uid(payments.provider_session_id) 유니크 제약의 실제 동작 검증 (실제 PostgreSQL)
 *
 * 왜 이 테스트가 필요한가
 * - 정기결제 재청구의 이중 청구 방어는 전부 이 제약 하나에 걸려 있다. PG 호출 전에 결정적
 *   merchant_uid 로 PENDING 을 선삽입하고, 중복 배달의 두 번째 삽입이 여기서 떨어지는 구조다.
 * - RecurringBillingServiceTest 는 목이 DataIntegrityViolationException 을 던지도록 흉내낼 뿐이다.
 *   DB 가 실제로 그 예외를 만들어 주는지는 목으로 증명할 수 없다. 제약이 빠지거나 유니크가 아니게
 *   바뀌면 단위 테스트는 그대로 통과하면서 운영에서는 두 건이 나란히 결제된다.
 *
 * 왜 H2 로는 안 되는가
 * - "NULL 은 서로 충돌하지 않는다"는 것이 기존 데이터를 깨지 않는 근거인데, NULL 과 유니크 인덱스의
 *   관계는 DB 제품 고유 동작이다. 운영이 PostgreSQL 이면 PostgreSQL 로 확인해야 한다.
 *
 * 트랜잭션 구성(명시)
 * - MembershipSubscriptionLockTest 와 같은 이유로 감싸는 트랜잭션을 끄고 TransactionTemplate 을 직접 쓴다.
 *   중복 삽입 경합을 보려면 서로 다른 커넥션의 트랜잭션 2개가 동시에 열려 있어야 한다.
 * - 슬라이스에는 Auditing 이 없으므로 not-null 인 createdAt/updatedAt 은 픽스처가 직접 채운다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 컨테이너 URL 을 쓰기 위해 자동 대체를 끈다
@Import(JpaSliceTestSupport.class)
@Testcontainers(disabledWithoutDocker = true)
@Tag("testcontainers") // testFast 가 제외하는 태그. 컨테이너를 띄우는 값이 비싸서 편집 직후 되먹임용 실행에서는 뺀다.
@TestPropertySource(
        properties = {
            // 엔티티 기준으로 스키마를 만든다. Payment.providerSessionId 의 unique 선언이 실제 인덱스가 되는지도 함께 검증된다.
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create", // create-drop 이 아니다: 종료 시 drop DDL 이 이미 내려간 컨테이너에 붙으려다 30초를 버린다
            "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
        })
@Transactional(propagation = Propagation.NOT_SUPPORTED) // @DataJpaTest 의 감싸는 트랜잭션을 끈다
class PaymentMerchantUidUniqueTest {

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

    /** 재청구 1차 시도의 merchant_uid. 중복 배달 두 건은 이 값이 똑같이 나온다. */
    private static final String REBILL_UID = "rebill_10_20260808_1_100";

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipPlanRepository planRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User user;
    private MembershipPlan plan;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        planRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.createLocalUser("billing@example.com", "encoded", "테스터"));
        plan = planRepository.save(MembershipPlan.createBasicPlan("Basic", "설명", new Money(9900L, "KRW"), 1));
    }

    private TransactionTemplate writeTransaction() {
        return new TransactionTemplate(transactionManager);
    }

    /** 재청구가 PG 호출 전에 선삽입하는 PENDING 결제 */
    private Payment pendingAttempt(String merchantUid) {
        Payment payment =
                Payment.createPendingPayment(user, plan, PaymentProvider.IMPORT, merchantUid, new Money(9900L, "KRW"));
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(payment, "createdAt", now); // 슬라이스에는 Auditing 이 없어 직접 채운다
        ReflectionTestUtils.setField(payment, "updatedAt", now);
        return payment;
    }

    private Throwable resultOrNullIfStillRunning(Future<Throwable> future, long seconds) throws Exception {
        try {
            return future.get(seconds, TimeUnit.SECONDS);
        } catch (TimeoutException stillBlocked) {
            return null;
        }
    }

    /**
     * 이 테스트가 이 파일의 존재 이유다. 유니크 제약을 지우거나 일반 인덱스로 되돌리면 여기서 잡힌다.
     */
    @Test
    @DisplayName("같은 merchant_uid 를 동시에 선삽입하면 두 번째가 제약 위반으로 떨어진다 - MQ 중복 배달 이중 청구 차단")
    void concurrentDuplicateInsertIsRejected() throws Exception {
        ExecutorService other = Executors.newSingleThreadExecutor();
        AtomicReference<Future<Throwable>> secondDelivery = new AtomicReference<>();
        try {
            writeTransaction().execute(status -> {
                paymentRepository.saveAndFlush(pendingAttempt(REBILL_UID)); // 첫 번째 배달: 선점(아직 커밋 전)

                // 두 번째 배달을 흉내낸다. 별도 스레드여야 별도 커넥션/트랜잭션이 열린다.
                secondDelivery.set(other.submit(() -> catchThrowable(() -> writeTransaction()
                        .execute(inner -> paymentRepository.saveAndFlush(pendingAttempt(REBILL_UID))))));

                // 첫 트랜잭션이 끝나기 전에는 유니크 인덱스에서 대기한다.
                // 여기서 이미 끝나 있으면 제약이 없어 그냥 두 건이 들어갔다는 뜻이다.
                try {
                    assertThat(resultOrNullIfStillRunning(secondDelivery.get(), 2))
                            .isNull();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return null; // 첫 번째 배달 커밋
            });

            Throwable second = secondDelivery.get().get(10, TimeUnit.SECONDS);
            assertThat(second).isInstanceOf(DataIntegrityViolationException.class);

            // 실제로 한 건만 남아야 한다. 이 시점에 2가 나오면 결제 API 가 두 번 호출됐을 상황이다.
            assertThat(paymentRepository.count()).isEqualTo(1);
        } finally {
            other.shutdownNow();
        }
    }

    @Test
    @DisplayName("서로 다른 시도(재시도 횟수/결제수단)는 막히지 않는다 - 폴백과 재시도가 살아 있어야 한다")
    void differentAttemptsAreNotBlocked() {
        paymentRepository.saveAndFlush(pendingAttempt("rebill_10_20260808_1_100")); // 1차, 기본 수단
        paymentRepository.saveAndFlush(pendingAttempt("rebill_10_20260808_1_200")); // 1차, 보조 수단(폴백)
        paymentRepository.saveAndFlush(pendingAttempt("rebill_10_20260808_2_100")); // 2차 재시도

        assertThat(paymentRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("merchant_uid 가 없는 결제는 여러 건 공존한다 - 유니크 추가가 기존 데이터를 깨지 않는 근거")
    void nullMerchantUidsDoNotCollide() {
        LocalDateTime now = LocalDateTime.now();
        // createSucceededPayment 는 providerSessionId 를 채우지 않는다(과거 재청구 성공 경로가 만든 형태).
        Payment first = Payment.createSucceededPayment(
                user, plan, PaymentProvider.IMPORT, "imp_1", new Money(9900L, "KRW"), now);
        ReflectionTestUtils.setField(first, "createdAt", now);
        ReflectionTestUtils.setField(first, "updatedAt", now);
        Payment second = Payment.createSucceededPayment(
                user, plan, PaymentProvider.IMPORT, "imp_2", new Money(9900L, "KRW"), now);
        ReflectionTestUtils.setField(second, "createdAt", now);
        ReflectionTestUtils.setField(second, "updatedAt", now);

        paymentRepository.saveAndFlush(first);
        paymentRepository.saveAndFlush(second);

        assertThat(first.getProviderSessionId()).isNull();
        assertThat(paymentRepository.count()).isEqualTo(2); // PostgreSQL 은 유니크 인덱스에서 NULL 을 충돌로 보지 않는다
    }
}
