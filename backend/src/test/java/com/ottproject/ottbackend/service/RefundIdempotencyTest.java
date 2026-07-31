package com.ottproject.ottbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.mybatis.PaymentQueryMapper;
import com.ottproject.ottbackend.repository.IdempotencyKeyRepository;
import com.ottproject.ottbackend.repository.JpaSliceTestSupport;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 환불의 이중 실행 방어 검증 (실제 PostgreSQL, 실제 커밋, 실제 스레드 2개)
 *
 * 왜 이 테스트가 필요한가
 * - refundIfEligible 은 락 없이 findById 로 읽은 상태로 "SUCCEEDED 인가"를 판정한 뒤 곧바로
 *   paymentGateway.issueRefund 를 불렀다. 조회는 과거를 알려줄 뿐 미래를 예약하지 않으므로,
 *   동시 요청 둘이 모두 가드를 통과하면 환불 API 가 두 번 나간다.
 * - 앞서 고친 확정 경로 셋과 다른 점은 되돌릴 수 없다는 것이다. 확정은 구독 행이나 아웃박스 행이
 *   두 개 생기는 것으로 끝나지만, 환불은 실제로 돈이 나가는 호출이다. 그래서 이 테스트가 세는 것은
 *   DB 행 수가 아니라 게이트웨이 호출 횟수다.
 * - 실제 이중 환불까지 가는지는 아임포트가 두 번째 요청을 거절해 주느냐에 달려 있어 미확인이다.
 *   남의 판정에 기대지 않겠다는 것이 이 수정의 요지이므로, 여기서도 호출 자체를 센다.
 *
 * 왜 락이 아니라 유니크 제약이 판정하는가
 * - 5절이 PG 호출을 트랜잭션 밖으로 밀어내므로(9절: 락 구간에 외부 호출 금지), 락은 3단계의 DB 쓰기만
 *   직렬화한다. 그 앞에서 이미 나가버린 환불 호출 두 번은 락으로 막을 수 없다.
 * - 그래서 호출 전에 payments 1건당 하나뿐인 결정적 키(payment.refund:{paymentId})를 선삽입해
 *   유니크 제약이 승자를 정하게 한다. 클라이언트 멱등키였다면 서로 다른 키를 들고 온 동시 요청
 *   두 건은 충돌하지 않아 둘 다 통과했을 것이다.
 *
 * 테스트 환경에 대한 메모(ARCHITECTURE 15절과의 차이)
 * - 15절은 @SpringBootTest + Testcontainers 를 요구하지만 이 프로젝트에는 @SpringBootTest 가 하나도
 *   없어 전체 컨텍스트를 띄우면 Redis/Kafka/RabbitMQ/메일까지 붙는다. 결함이 서비스-DB 경계에 있으므로
 *   PaymentCheckoutIdempotencyTest 와 같은 슬라이스 구성을 따랐다.
 *   핵심 요구(실제 커밋, 실제 스레드 2개, 테스트 메서드에 @Transactional 없음)는 그대로 지킨다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 컨테이너 URL 을 쓰기 위해 자동 대체를 끈다
@Import({JpaSliceTestSupport.class, PaymentCommandService.class})
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        // 엔티티 기준으로 스키마를 만든다. IdempotencyKey.keyValue 의 unique 선언이 실제 인덱스가 되는지도 함께 검증된다.
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED) // @DataJpaTest 의 감싸는 트랜잭션을 끈다
class RefundIdempotencyTest {

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

    /** 환불 경로는 쓰지 않지만 PaymentCommandService 의 생성자 의존성이라 빈이 필요하다. */
    @TestConfiguration
    static class ObjectMapperTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private static final String IMP_UID = "imp_refund_1"; // 환불 대상 결제의 외부 결제 ID
    private static final long PRICE = 9900L; // 결제 금액(전액 환불 대상)

    @Autowired
    private PaymentCommandService service;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private MembershipSubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipPlanRepository planRepository;

    @MockitoBean
    private ImportPaymentGateway paymentGateway; // 돈이 나가는 호출. 몇 번 불렸는지가 이 테스트의 관심사다

    @MockitoBean
    private PlayerProgressReadService playerProgressReadService;

    @MockitoBean
    private PaymentQueryMapper paymentQueryMapper;

    @MockitoBean
    private PaymentMethodService paymentMethodService;

    @MockitoBean
    private MembershipCommandService membershipCommandService;

    @MockitoBean
    private RecurringBillingService recurringBillingService;

    private Long userId;
    private Long paymentId;

    /** 첫 요청이 환불권 선점 직전(트랜잭션 안, 커밋 전)에 도달했음을 알린다 */
    private CountDownLatch firstInsideClaim;
    /** 첫 요청을 진행시키는 신호 */
    private CountDownLatch releaseFirst;
    /** 시청 이력 조회에 몇 번 들어왔는가 = 몇 번째 요청인가 */
    private AtomicInteger eligibilityChecks;
    /** 환불 API 가 몇 번 나갔는가 = 돈이 몇 번 빠져나갔는가 */
    private AtomicInteger refundCalls;

    @BeforeEach
    void setUp() {
        idempotencyKeyRepository.deleteAll();
        paymentRepository.deleteAll();
        subscriptionRepository.deleteAll();
        planRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(User.createLocalUser("refund@example.com", "encoded", "테스터"));
        MembershipPlan plan = planRepository.save(
                MembershipPlan.createBasicPlan("Basic", "기본", new Money(PRICE, "KRW"), 1));
        userId = user.getId();

        LocalDateTime now = LocalDateTime.now();
        subscriptionRepository.save(MembershipSubscription.createSubscription(
                user, plan, now.minusDays(3), now.plusDays(27))); // ACTIVE — 환불되면 즉시 해지돼야 한다

        Payment payment = Payment.createSucceededPayment(
                user, plan, PaymentProvider.IMPORT, IMP_UID, new Money(PRICE, "KRW"), now.minusDays(3));
        ReflectionTestUtils.setField(payment, "createdAt", now); // 슬라이스에는 Auditing 이 없어 직접 채운다
        ReflectionTestUtils.setField(payment, "updatedAt", now);
        paymentId = paymentRepository.save(payment).getId();

        firstInsideClaim = new CountDownLatch(1);
        releaseFirst = new CountDownLatch(1);
        eligibilityChecks = new AtomicInteger();
        refundCalls = new AtomicInteger();

        given(paymentGateway.issueRefund(anyString(), anyLong())).willAnswer(invocation -> {
            refundCalls.incrementAndGet();
            PaymentGateway.RefundResult rr = new PaymentGateway.RefundResult();
            rr.providerRefundId = invocation.getArgument(0);
            rr.refundedAt = LocalDateTime.now();
            return rr;
        });
    }

    /**
     * 시청 이력 조회를 붙잡는 이유
     * - 환불권 선점 직전이면서 claimRefund 트랜잭션 안인 지점이 여기뿐이다. 확정 경로들과 달리 환불은
     *   PG 호출이 트랜잭션 밖으로 나가 있어서, 그 호출을 래치로 쓰면 락/제약 구간을 재현하지 못한다.
     * - 첫 요청을 여기 세워두면 "SUCCEEDED 를 읽고 아직 아무것도 선점하지 않은" 상태가 그대로 유지된다.
     *   고치기 전 코드가 두 번째 환불을 내보내던 바로 그 창이다.
     */
    private void holdFirstCallerBeforeClaim() {
        given(playerProgressReadService.sumWatchedSecondsSincePaidEpisodes(eq(userId), any()))
                .willAnswer(invocation -> {
                    if (eligibilityChecks.incrementAndGet() == 1) {
                        firstInsideClaim.countDown();
                        releaseFirst.await(10, TimeUnit.SECONDS);
                    }
                    return 0; // 미시청 = 환불 가능
                });
    }

    private String refundOutcome() {
        try {
            service.refundIfEligible(userId, paymentId);
            return "refunded";
        } catch (ResponseStatusException e) {
            return "rejected-" + e.getStatusCode().value();
        }
    }

    /**
     * 이 테스트가 이 파일의 존재 이유다.
     * claimRefund 의 멱등키 선삽입을 지우거나 조회 후 분기로 되돌리면 여기서 잡힌다.
     */
    @Test
    @DisplayName("환불 요청이 겹쳐도 환불 API 는 한 번만 나간다 - 이중 환불 차단")
    void concurrentRefundCallsGatewayOnce() throws Exception {
        holdFirstCallerBeforeClaim();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // 첫 요청: 결제를 SUCCEEDED 로 읽은 채 선점 직전에 멈춰 선다.
            Future<String> first = pool.submit(this::refundOutcome);
            assertThat(firstInsideClaim.await(30, TimeUnit.SECONDS)).isTrue();

            // 두 번째 요청. 별도 스레드여야 별도 커넥션/트랜잭션이 열린다.
            // 첫 요청이 멈춰 있는 동안 끝까지 진행해 환불을 마친다.
            Future<String> second = pool.submit(this::refundOutcome);
            assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo("refunded");

            releaseFirst.countDown(); // 첫 요청 재개 — 낡은 "SUCCEEDED" 를 들고 있다

            // 결과를 문자열로 돌려주는 이유: catchThrowable 로 감싸면 "성공"과 "아직 대기 중"이 둘 다
            // null 이라 구별되지 않는다. 선점을 없애도 통과해 버리는 가짜 테스트가 된다.
            assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo("rejected-409");

            // 이 파일이 지키려는 단 하나의 사실: 돈이 나가는 호출은 한 번뿐이다.
            assertThat(refundCalls.get()).isEqualTo(1);
            assertThat(idempotencyKeyRepository.count()).isEqualTo(1);

            Payment refunded = paymentRepository.findById(paymentId).orElseThrow();
            assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(refunded.getRefundedAmount()).isEqualTo(PRICE); // 전액
        } finally {
            releaseFirst.countDown(); // 실패 경로에서도 첫 요청이 매달려 있지 않게 한다
            // 단언이 먼저 깨져도 남은 스레드가 다음 테스트의 픽스처 위에 커밋하지 않도록 여기서 끝을 본다.
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * 위 테스트는 첫 요청이 두 번째의 커밋을 뒤늦게 보는 순서였다(빠른 경로 조회가 걸러낸다).
     * 여기서는 둘을 같은 지점에서 동시에 풀어, 둘 다 조회를 통과한 뒤 INSERT 에서 부딪히게 한다.
     * 6절이 "조회 후 분기로 판정하지 말고 유니크 제약에 INSERT 로 물어보라"고 하는 이유가 이 순서다.
     */
    @Test
    @DisplayName("두 환불 요청이 같은 순간에 선점을 시도하면 유니크 제약이 승자를 정한다")
    void simultaneousRefundClaimsAreDecidedByUniqueConstraint() throws Exception {
        CyclicBarrier bothInsideClaim = new CyclicBarrier(2);
        given(playerProgressReadService.sumWatchedSecondsSincePaidEpisodes(eq(userId), any()))
                .willAnswer(invocation -> {
                    bothInsideClaim.await(30, TimeUnit.SECONDS); // 둘 다 선점 직전에 모인 뒤 함께 출발
                    return 0;
                });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = pool.submit(this::refundOutcome);
            Future<String> b = pool.submit(this::refundOutcome);

            List<String> outcomes = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));

            // 어느 쪽이 이기는지는 정해져 있지 않다. 정해져 있는 것은 승자가 정확히 하나라는 것이다.
            assertThat(outcomes).containsExactlyInAnyOrder("refunded", "rejected-409");
            assertThat(refundCalls.get()).isEqualTo(1);
            assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
            assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.REFUNDED);
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("환불이 끝난 뒤 같은 결제를 다시 환불하면 400 - 기존 동작을 바꾸지 않는다")
    void sequentialRefundIsRejected() {
        given(playerProgressReadService.sumWatchedSecondsSincePaidEpisodes(eq(userId), any())).willReturn(0);

        service.refundIfEligible(userId, paymentId);

        // 순차 재요청은 선점까지 가지 않는다. 상태 가드(SUCCEEDED 아님)가 먼저 걸러 기존과 같은 400 이다.
        // 409 는 "아직 진행 중이거나 결과를 모르는" 경우에만 나온다.
        Throwable thrown = catchThrowable(() -> service.refundIfEligible(userId, paymentId));

        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode().value()).isEqualTo(400);
        assertThat(thrown).hasMessageContaining("환불 대상 결제가 아닙니다");
        assertThat(refundCalls.get()).isEqualTo(1);

        // 환불되면 구독은 즉시 해지된다(정책) — 락 분리가 정상 흐름을 잡아먹지 않았는지 함께 본다
        assertThat(subscriptionRepository.findAll())
                .singleElement()
                .satisfies(sub -> {
                    assertThat(sub.getStatus()).isEqualTo(MembershipSubscriptionStatus.CANCELED);
                    assertThat(sub.isAutoRenew()).isFalse();
                });
    }

    /**
     * 선점을 정책 검증 뒤에 두는 이유를 못박는다.
     * 키는 결제당 하나뿐이고 반납 수단이 없으므로, 정책으로 거절된 요청이 키를 태우면
     * 그 결제는 이후의 정당한 환불까지 영구히 409 가 된다.
     */
    @Test
    @DisplayName("정책 위반으로 거절된 요청은 환불권을 태우지 않는다 - 이후 정당한 환불이 막히면 안 된다")
    void rejectedByPolicyDoesNotBurnTheClaim() {
        given(playerProgressReadService.sumWatchedSecondsSincePaidEpisodes(eq(userId), any()))
                .willReturn(1, 0); // 첫 요청은 시청 이력 1초, 두 번째는 미시청

        Throwable thrown = catchThrowable(() -> service.refundIfEligible(userId, paymentId));
        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode().value()).isEqualTo(400);
        assertThat(idempotencyKeyRepository.count()).isZero(); // 키가 남아 있으면 안 된다
        verify(paymentGateway, never()).issueRefund(anyString(), anyLong());

        // 시청 이력이 정리된 뒤의 정당한 환불은 그대로 통과해야 한다
        service.refundIfEligible(userId, paymentId);

        assertThat(refundCalls.get()).isEqualTo(1);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
    }
}
