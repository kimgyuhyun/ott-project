package com.ottproject.ottbackend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.ottproject.ottbackend.dto.MembershipSubscribeRequestDto;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.PaymentMethod;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.PaymentMethodType;
import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.MyBatisSliceTestSupport;
import com.ottproject.ottbackend.repository.PaymentMethodRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 구독 중 재결제(#193) 이중 청구 재현 (실제 PostgreSQL, 실제 매퍼 SQL, 실제 커밋)
 *
 * 무엇을 재현하나
 * - 체크아웃에 "활성 구독 있음" 검사가 없어서, 구독 중인 사용자가 한 번 더 결제하면 결제 확정 경로
 *   (PaymentCommandService.markSucceededAndProvision)가 MembershipCommandService.subscribe() 를 다시 부른다.
 *   subscribe() 는 기존 구독을 연장하지 않고 남은 기간 끝부터 시작하는 구독 행을 하나 더 만든다.
 * - 정기결제 배치는 구독 행 단위로 대상을 고르고(findSubscriptionsForBilling) 같은 사용자의 다른 행을 보지 않는다.
 *   merchant_uid 도 구독 id 를 품으므로 두 행의 청구는 서로를 막지 못한다.
 * - 이 테스트는 결제 확정이 하는 일(subscribe 호출)을 그대로 부르고, 시각은 구독 행의 시각 컬럼을
 *   한 주기만큼 과거로 당겨서 흘린다. 배치는 실제 now 로 돌므로 서비스 코드는 손대지 않는다.
 *
 * 테스트가 지키는 불변식(정책 1 "막기"와 정책 2 "연장" 어느 쪽을 골라도 같다)
 * - 이미 결제한 기간에는 다시 청구하지 않는다.
 * - 한 청구 주기에 사용자당 청구는 최대 1건이다.
 * - 해지한 사용자에게는 더 청구하지 않는다.
 * - 결제한 개월 수만큼 이용 기간이 늘어난다(동시 확정에서 기간이 겹쳐 사라지지 않는다).
 *
 * 테스트 환경 선택 근거(ARCHITECTURE 15절)
 * - 결함이 매퍼 SQL 의 대상 선정과 서비스-DB 경계에 있어 목으로는 재현되지 않는다. 슬라이스에 검증 대상 서비스를
 *   실물로 넣고, 감싸는 트랜잭션을 꺼서 서비스가 자기 트랜잭션으로 진짜 커밋하게 한다.
 * - 스키마는 마이그레이션으로 만들고 ddl-auto 는 validate 다. 고칠 때 부분 유니크 인덱스 같은 제약을 넣으면
 *   이 테스트가 그 제약을 실제로 지나가야 하기 때문이다.
 * - 결제사 호출만 목이다. 호출 횟수가 곧 청구 횟수다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 컨테이너 URL 을 쓰기 위해 자동 대체를 끈다
@Import({
    MyBatisSliceTestSupport.class, // 배치 대상 선정 SQL 을 실제 매퍼 XML 로 실행한다
    MembershipCommandService.class,
    RecurringBillingService.class,
    BillingAttemptRecorder.class
})
@Testcontainers(disabledWithoutDocker = true)
@Tag("testcontainers") // testFast 가 제외하는 태그. 컨테이너를 띄우는 값이 비싸서 편집 직후 되먹임용 실행에서는 뺀다.
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.datasource.hikari.maximum-pool-size=20", // 동시 확정 스레드가 커넥션 대기로 줄 서지 않게
            // 배치가 PG 호출 전에 스스로 만드는 PENDING 결제의 createdAt 을 채운다(인터셉터 주석 참고)
            "spring.jpa.properties.hibernate.session_factory.interceptor="
                    + "com.ottproject.ottbackend.repository.SliceAuditTimestampInterceptor"
        })
@Transactional(propagation = Propagation.NOT_SUPPORTED) // @DataJpaTest 의 감싸는 트랜잭션을 끈다
class RepayWhileActiveDoubleBillingTest {

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

    /** 배치가 생존 신호 게이지를 등록한다. 슬라이스에는 메트릭 자동설정이 없다. */
    @TestConfiguration
    static class MeterRegistryTestConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private static final long PRICE = 9900L;

    @Autowired
    private MembershipCommandService membershipCommandService;

    @Autowired
    private RecurringBillingService recurringBillingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipPlanRepository planRepository;

    @Autowired
    private PaymentMethodRepository paymentMethodRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @MockitoBean
    private MembershipNotificationService notificationService;

    @MockitoBean
    private BillingRetryPublisher billingRetryPublisher;

    /** 결제사 저장수단 청구 호출 수 = 실제로 돈이 나간 횟수 */
    private AtomicInteger charges;

    private String planCode;

    @BeforeEach
    void setUp() {
        // 롤백이 없으므로 직접 지운다. users/plans 를 참조하는 행(구독·결제·결제수단)이 CASCADE 로 함께 지워진다.
        jdbcTemplate.execute("TRUNCATE users, plans RESTART IDENTITY CASCADE");

        MembershipPlan plan =
                planRepository.save(MembershipPlan.createBasicPlan("Basic", "설명", new Money(PRICE, "KRW"), 1));
        planCode = plan.getCode();

        charges = new AtomicInteger();
        given(paymentGateway.chargeWithSavedMethod(
                        anyString(), anyString(), anyString(), anyLong(), anyString(), any()))
                .willAnswer(inv -> {
                    PaymentGateway.ChargeResult result = new PaymentGateway.ChargeResult();
                    result.providerPaymentId = "imp_rebill_" + charges.incrementAndGet();
                    result.paidAt = LocalDateTime.now();
                    return result;
                });
    }

    @Test
    @DisplayName("구독 중 재결제 후 두 주기 동안, 이미 결제한 기간에 청구하지 않고 주기마다 1건만 청구한다")
    void repayWhileActive_doesNotChargeTwice() {
        Long userId = createUserWithBillingKey("repay@example.com");

        pay(userId); // 첫 결제: 구독 A [t0, t0+1M)
        pay(userId); // 구독 중 재결제: 결제 확정이 subscribe() 를 한 번 더 부른다
        int rowsAfterRepay = subscriptionRows(userId);
        int billableRowsAfterRepay = billableRows(userId);

        // 주기 1: 첫 결제분 만료. 그 다음 한 달은 재결제로 이미 돈을 냈다.
        shiftSubscriptionsBack(userId, 1);
        int before = charges.get();
        recurringBillingService.runRecurringBilling();
        int cycle1 = charges.get() - before;

        // 주기 2: 재결제분 만료. 여기서 처음으로 새 청구가 1건 나가야 한다.
        shiftSubscriptionsBack(userId, 2);
        before = charges.get();
        recurringBillingService.runRecurringBilling();
        int cycle2 = charges.get() - before;

        int succeededRebills = succeededRebillPayments(userId);
        System.out.printf(
                "[#193 재결제] 구독 행=%d, 청구 대상 행=%d, 주기1 청구=%d, 주기2 청구=%d, 정기결제 성공 행=%d, 정기결제 합계=%d원%n",
                rowsAfterRepay, billableRowsAfterRepay, cycle1, cycle2, succeededRebills, succeededRebills * PRICE);

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(billableRowsAfterRepay).as("재결제 후 청구 대상 구독 행 수").isEqualTo(1);
        soft.assertThat(cycle1).as("주기 1 청구 수 (이미 결제한 기간)").isZero();
        soft.assertThat(cycle2).as("주기 2 청구 수").isEqualTo(1);
        soft.assertAll();
    }

    @Test
    @DisplayName("구독 중 재결제 후 해지하면 이후 어느 주기에도 청구하지 않는다")
    void repayWhileActive_thenCancel_neverChargesAgain() {
        Long userId = createUserWithBillingKey("cancel@example.com");

        pay(userId);
        pay(userId);
        membershipCommandService.cancel(userId, null); // 말일 해지 예약(멱등키 없이)
        int stillBillable = billableRows(userId);

        int before = charges.get();
        shiftSubscriptionsBack(userId, 1);
        recurringBillingService.runRecurringBilling();
        shiftSubscriptionsBack(userId, 2);
        recurringBillingService.runRecurringBilling();
        int afterCancel = charges.get() - before;

        System.out.printf("[#193 해지] 해지 후 청구 대상으로 남은 행=%d, 해지 후 두 주기 청구=%d%n", stillBillable, afterCancel);

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(stillBillable).as("해지 후 청구 대상으로 남은 구독 행 수").isZero();
        soft.assertThat(afterCancel).as("해지 후 두 주기 동안의 청구 수").isZero();
        soft.assertAll();
    }

    @Test
    @DisplayName("구독 중 결제 2건이 동시에 확정돼도 결제한 개월 수만큼 이용 기간이 늘어난다")
    void concurrentConfirms_doNotOverlapPeriods() throws Exception {
        int rounds = 20; // 같은 경합을 여러 번 걸어 발생 비율을 센다
        int threads = 2; // 두 탭에서 각각 결제를 마친 상황
        int overlappedRounds = 0;
        int lostMonths = 0;

        for (int r = 0; r < rounds; r++) {
            Long userId = createUserWithBillingKey("race" + r + "@example.com");
            pay(userId); // 기존 활성 구독
            LocalDateTime firstEnd = latestEnd(userId);

            ExecutorService pool = Executors.newFixedThreadPool(threads); // threads 와 같아야 ready 가 끝난다
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            List<Throwable> errors = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await(); // 동시에 출발
                        pay(userId);
                    } catch (Throwable e) {
                        synchronized (errors) {
                            errors.add(e);
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("준비되지 않은 스레드가 있다");
            start.countDown();
            if (!done.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("끝나지 않은 스레드가 있다");
            pool.shutdown();
            if (!errors.isEmpty()) throw new IllegalStateException("확정 스레드 실패", errors.get(0));

            // 결제 2건이면 기존 만료일에서 두 달이 늘어나야 한다
            LocalDateTime expectedEnd = firstEnd.plusMonths(threads);
            LocalDateTime actualEnd = latestEnd(userId);
            if (actualEnd.isBefore(expectedEnd)) {
                overlappedRounds++;
                lostMonths += monthsBetween(actualEnd, expectedEnd);
            }
        }

        System.out.printf(
                "[#193 동시 확정] %d회 중 기간 겹침=%d회, 결제했는데 사라진 이용 기간=%d개월(=%d원)%n",
                rounds, overlappedRounds, lostMonths, lostMonths * PRICE);

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(overlappedRounds).as("동시 확정 %d회 중 기간이 겹친 횟수", rounds).isZero();
        soft.assertAll();
    }

    // ===== 픽스처와 측정 =====

    private Long createUserWithBillingKey(String email) {
        User user = userRepository.save(User.createLocalUser(email, "encoded", "테스터"));
        PaymentMethod method = PaymentMethod.createPaymentMethod(
                user, PaymentProvider.IMPORT, PaymentMethodType.KAKAO_PAY, "cuid_" + email);
        method.applyListingOptions(true, 1, null);
        paymentMethodRepository.save(method);
        return user.getId();
    }

    /** 결제 확정이 멤버십에 하는 일 그대로: 같은 플랜 코드로 subscribe() 를 부른다 */
    private void pay(Long userId) {
        MembershipSubscribeRequestDto dto = new MembershipSubscribeRequestDto();
        dto.planCode = planCode;
        membershipCommandService.subscribe(userId, dto);
    }

    /**
     * 청구 주기 하나를 흘린다: 이 사용자의 구독 시각을 한 달 + cycle 일 과거로 당긴다.
     * 다음 청구일이 가장 이른 행이 방금 만료된 상태가 된다. 결제 행은 건드리지 않는다.
     *
     * 더 당기는 일수를 주기마다 다르게 두는 이유: 재청구 merchant_uid 의 주기 앵커는 endAt 의 "날짜"다.
     * 매번 같은 폭으로 당기면 연장된 endAt 이 직전 주기와 같은 날짜로 돌아와 앵커가 겹치고,
     * 그 청구가 중복 방어(ux_payments_merchant_uid)에 막혀 실제보다 적게 세어진다(실측: 주기 2 청구가 2 대신 1).
     * 운영에서는 주기마다 endAt 날짜가 달라 이런 겹침이 없다.
     */
    private void shiftSubscriptionsBack(Long userId, int cycle) {
        jdbcTemplate.update(
                """
                UPDATE subscriptions
                   SET start_at = start_at - interval '1 month' - make_interval(days => ?),
                       end_at = end_at - interval '1 month' - make_interval(days => ?),
                       next_billing_at = next_billing_at - interval '1 month' - make_interval(days => ?)
                 WHERE user_id = ?
                """,
                cycle,
                cycle,
                cycle,
                userId);
    }

    private int subscriptionRows(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM subscriptions WHERE user_id = ?", Integer.class, userId);
    }

    /** 정기결제 배치가 언젠가 청구할 행: 매퍼 SQL 의 게이트 조건에서 시각 조건만 뺀 것 */
    private int billableRows(Long userId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM subscriptions
                 WHERE user_id = ? AND status IN ('ACTIVE', 'PAST_DUE')
                   AND auto_renew = true AND cancel_at_period_end = false AND next_billing_at IS NOT NULL
                """,
                Integer.class,
                userId);
    }

    private int succeededRebillPayments(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payments WHERE user_id = ? AND status = 'SUCCEEDED'", Integer.class, userId);
    }

    private LocalDateTime latestEnd(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT max(end_at) FROM subscriptions WHERE user_id = ?", LocalDateTime.class, userId);
    }

    private static int monthsBetween(LocalDateTime from, LocalDateTime to) {
        int months = 0;
        while (from.plusMonths(months).isBefore(to)) {
            months++;
        }
        return months;
    }
}
