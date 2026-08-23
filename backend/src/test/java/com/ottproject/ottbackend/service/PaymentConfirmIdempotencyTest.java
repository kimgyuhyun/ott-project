package com.ottproject.ottbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ottproject.ottbackend.dto.PaymentWebhookEventDto;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.mybatis.PaymentQueryMapper;
import com.ottproject.ottbackend.repository.IdempotencyKeyRepository;
import com.ottproject.ottbackend.repository.JpaSliceTestSupport;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.OutboxEventRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 결제 확정의 이중 지급 방어 검증 (실제 PostgreSQL, 실제 커밋, 실제 스레드 2개)
 *
 * 왜 이 테스트가 필요한가
 * - 확정 경로 셋(클라 확정 / 웹훅 / 대사 배치)이 모두 markSucceededAndProvision 으로 수렴하는데,
 *   그 멱등 가드는 "이미 SUCCEEDED 면 return" 이다. 예전에는 세 경로 모두 락 없는 findById 로 읽고
 *   그 상태로 가드를 통과했다. 조회는 과거를 알려줄 뿐 미래를 예약하지 않으므로 동시 요청 둘 다
 *   "내가 첫 번째"라는 결론을 냈고, 결제 1건에 구독 2건·아웃박스 2건이 생겼다.
 * - 경합이 예외적인 상황이 아니라는 게 핵심이다. 클라 확정과 웹훅은 설계상 동시에 일어나는 정상 흐름이고,
 *   확정 전 PG 재검증이 트랜잭션 안에 있어서 경합 창이 PortOne 응답 시간만큼 벌어져 있었다.
 * - PaymentCommandServiceTest 는 목이 Optional 을 돌려주는 것만 확인한다. 두 번째 요청이 첫 요청의
 *   커밋까지 실제로 대기하는지, 대기 후 새 상태를 보는지는 목으로 증명할 수 없다.
 *   락이 빠지면 목 기반 테스트는 전부 통과하는 채로 운영에서 멤버십이 두 번 지급된다(ARCHITECTURE 15절).
 * - webhook 의 eventId 멱등키는 이 경합을 못 막는다. 그건 같은 웹훅의 재전송만 막을 뿐,
 *   웹훅 대 클라 확정처럼 서로 다른 경로가 같은 결제를 확정하는 것과는 무관하다.
 *
 * 테스트 환경 선택 근거(ARCHITECTURE 15절)
 * - 15절은 동시성/멱등 재현에 실제 커밋과 실제 스레드를 요구하고, 컨텍스트 범위는 @SpringBootTest 든
 *   검증 대상 서비스를 @Import 한 슬라이스든 무방하다고 둔다. 결함이 서비스-DB 경계에 있으므로 슬라이스를
 *   골랐다. 전체 컨텍스트는 Redis/Kafka/RabbitMQ/메일과 스케줄러까지 끌어와 재현과 무관한 실패를 만든다.
 *   같은 이유로 슬라이스를 쓰는 MembershipCancelIdempotencyTest / PaymentMerchantUidUniqueTest 와 같은 구성이다.
 * - 15절이 요구하는 것(실제 커밋, 실제 스레드 2개, 테스트 메서드에 @Transactional 없음,
 *   서비스는 목이 아닌 실물 주입)은 그대로 지킨다.
 * - 감싸는 트랜잭션을 끄는 이유도 같다. 서비스가 자기 @Transactional 로 진짜 커밋을 해야 경합이 재현된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 컨테이너 URL 을 쓰기 위해 자동 대체를 끈다
@Import({JpaSliceTestSupport.class, PaymentCommandService.class, MembershipCommandService.class})
@Testcontainers(disabledWithoutDocker = true)
@Tag("testcontainers") // testFast 가 제외하는 태그. 컨테이너를 띄우는 값이 비싸서 편집 직후 되먹임용 실행에서는 뺀다.
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create", // create-drop 이 아니다: 종료 시 drop DDL 이 이미 내려간 컨테이너에 붙으려다 30초를 버린다
            "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
        })
@Transactional(propagation = Propagation.NOT_SUPPORTED) // @DataJpaTest 의 감싸는 트랜잭션을 끈다
class PaymentConfirmIdempotencyTest {

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

    /**
     * 아웃박스 페이로드 직렬화에 필요하다(@DataJpaTest 슬라이스에는 Jackson 자동설정이 없다).
     * PaymentSucceededEventDto 에 LocalDateTime 이 있어 JavaTimeModule 이 없으면 적재가 실패한다.
     */
    @TestConfiguration
    static class ObjectMapperTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }
    }

    private static final String MERCHANT_UID = "sess_confirm_1"; // 이 결제의 merchant_uid
    private static final String WEBHOOK_IMP_UID = "imp_webhook"; // 웹훅이 들고 온 외부 결제 ID
    private static final String CLIENT_IMP_UID = "imp_client"; // 클라 확정이 들고 온 외부 결제 ID

    @Autowired
    private PaymentCommandService service;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MembershipSubscriptionRepository subscriptionRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipPlanRepository planRepository;

    // 확정 경로는 PaymentGateway 계약만 쓴다. 구현체를 알 필요가 없으므로 인터페이스로 목을 만든다.
    @MockitoBean
    private PaymentGateway paymentGateway;

    @MockitoBean
    private PaymentQueryMapper paymentQueryMapper;

    @MockitoBean
    private PaymentMethodService paymentMethodService;

    @MockitoBean
    private PlayerProgressReadService playerProgressReadService;

    @MockitoBean
    private RecurringBillingService recurringBillingService;

    @MockitoBean
    private MembershipNotificationService notificationService;

    // 확정 트랜잭션 안(락 보유 중)에 붙잡을 지점. 실제 직렬화는 그대로 수행해야 아웃박스가 정상 적재된다.
    // 예전에는 fetchPaymentDetails 를 붙잡았는데, 그 호출이 확정 트랜잭션 밖으로 나가면서 없어졌다.
    @MockitoSpyBean
    private ObjectMapper objectMapper;

    private Long userId;
    private Long paymentId;

    /** 첫 확정이 아웃박스 적재 직전(락 보유, 커밋 전)에 도달했음을 알린다 */
    private CountDownLatch firstReachedOutbox;
    /** 첫 확정을 커밋시키는 신호 */
    private CountDownLatch releaseFirst;
    /** 확정 마무리 단계에 몇 번 들어왔는가 = 실제로 몇 번 지급했는가 */
    private AtomicInteger provisioningCalls;

    @BeforeEach
    void setUp() throws Exception { // writeValueAsString 스터빙이 검사 예외를 선언한다
        outboxEventRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        paymentRepository.deleteAll();
        subscriptionRepository.deleteAll();
        planRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(User.createLocalUser("confirm@example.com", "encoded", "테스터"));
        MembershipPlan plan =
                planRepository.save(MembershipPlan.createBasicPlan("Basic", "설명", new Money(9900L, "KRW"), 1));
        userId = user.getId();

        Payment payment =
                Payment.createPendingPayment(user, plan, PaymentProvider.IMPORT, MERCHANT_UID, new Money(9900L, "KRW"));
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(payment, "createdAt", now); // 슬라이스에는 Auditing 이 없어 직접 채운다
        ReflectionTestUtils.setField(payment, "updatedAt", now);
        paymentId = paymentRepository.save(payment).getId();

        firstReachedOutbox = new CountDownLatch(1);
        releaseFirst = new CountDownLatch(1);
        provisioningCalls = new AtomicInteger();

        // 클라 확정의 PG 재검증은 통과시킨다(이 테스트의 관심사가 아니다).
        given(paymentGateway.verifyPayment(anyString(), anyString(), anyLong())).willReturn(true);

        // 지급 단계에 첫 확정을 붙잡아 둔다.
        // 아웃박스 직렬화는 결제 행을 잠그고 SUCCEEDED 로 바꾸고 구독을 만든 뒤, 커밋하기 전에 불린다.
        // 즉 여기서 멈춘 스레드는 락을 쥔 채 아직 커밋하지 않은 상태다.
        doAnswer(invocation -> {
                    if (provisioningCalls.incrementAndGet() == 1) {
                        firstReachedOutbox.countDown();
                        releaseFirst.await(10, TimeUnit.SECONDS);
                    }
                    return invocation.callRealMethod(); // 실제 직렬화는 그대로 수행
                })
                .when(objectMapper)
                .writeValueAsString(any());
    }

    /** 결제 성공 웹훅 1건 */
    private PaymentWebhookEventDto succeededWebhook() {
        PaymentWebhookEventDto e = new PaymentWebhookEventDto();
        e.eventId = WEBHOOK_IMP_UID + ":SUCCEEDED";
        e.status = PaymentStatus.SUCCEEDED;
        e.providerPaymentId = WEBHOOK_IMP_UID;
        e.occurredAt = LocalDateTime.now();
        return e;
    }

    /** 아임포트 역조회 결과(대사 배치용) */
    private PaymentGateway.ReconcileResult paidResult() {
        PaymentGateway.ReconcileResult r = new PaymentGateway.ReconcileResult();
        r.found = true;
        r.status = PaymentGateway.ReconcileStatus.PAID;
        r.providerPaymentId = "imp_reconcile";
        r.amount = 9900L;
        return r;
    }

    private <T> T resultOrNullIfStillRunning(Future<T> future, long seconds) throws Exception {
        try {
            return future.get(seconds, TimeUnit.SECONDS);
        } catch (TimeoutException stillBlocked) {
            return null;
        }
    }

    /**
     * 이 테스트가 이 파일의 존재 이유다.
     * confirmSucceeded 의 findByIdForUpdate 를 findById 로 되돌리면 여기서 잡힌다.
     */
    @Test
    @DisplayName("웹훅이 확정 중일 때 클라 확정이 들어와도 멤버십은 한 번만 지급된다 - 이중 지급 차단")
    void concurrentWebhookAndClientConfirmProvisionsOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // 첫 번째: 웹훅 확정. 결제 행을 잠그고 지급 단계에서 멈춘다(아직 커밋 전).
            Future<Throwable> webhook =
                    pool.submit(() -> catchThrowable(() -> service.applyWebhookEvent(paymentId, succeededWebhook())));
            assertThat(firstReachedOutbox.await(30, TimeUnit.SECONDS)).isTrue();

            // 두 번째: 클라 확정. 별도 스레드여야 별도 커넥션/트랜잭션이 열린다.
            // 성공 여부가 아니라 "끝났는가"를 봐야 하므로 완료 표식을 돌려준다.
            // catchThrowable 로 감싸면 성공했을 때도 null 이라 대기 중과 구별할 수 없다.
            Future<String> client = pool.submit(() -> {
                service.completePayment(userId, paymentId, CLIENT_IMP_UID);
                return "completed";
            });

            // 웹훅이 커밋하기 전에는 결제 행 락에서 대기해야 한다.
            // 여기서 이미 끝나 있으면 락이 없다는 뜻이고, 그건 곧 낡은 PENDING 을 보고 또 지급했다는 뜻이다.
            assertThat(resultOrNullIfStillRunning(client, 3)).isNull();

            releaseFirst.countDown(); // 웹훅 커밋

            assertThat(webhook.get(30, TimeUnit.SECONDS)).isNull(); // 웹훅은 정상 완료
            assertThat(client.get(30, TimeUnit.SECONDS)).isEqualTo("completed"); // 클라 확정은 조용히 성공(멱등)

            // 사용자에게 보이는 증상: 결제 1건에 구독이 2개 생기면 안 된다
            assertThat(subscriptionRepository.count()).isEqualTo(1);
            // 아웃박스 2건이면 영수증 메일도 2통 나간다
            assertThat(outboxEventRepository.count()).isEqualTo(1);
            // 지급 단계 자체를 한 번만 지나야 한다(두 번째는 가드에서 멈춘다)
            assertThat(provisioningCalls.get()).isEqualTo(1);

            Payment confirmed = paymentRepository.findById(paymentId).orElseThrow();
            assertThat(confirmed.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            // 먼저 확정한 쪽의 imp_uid 가 남아야 한다(뒤에 온 쪽이 덮어쓰면 확정 기록이 흔들린다)
            assertThat(confirmed.getProviderPaymentId()).isEqualTo(WEBHOOK_IMP_UID);
        } finally {
            releaseFirst.countDown(); // 실패 경로에서도 첫 확정이 매달려 있지 않게 한다
            // 단언이 먼저 깨져도 남은 스레드가 다음 테스트의 픽스처 위에 커밋하지 않도록 여기서 끝을 본다.
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * 대사 배치는 세 번째 확정 경로다. 아임포트에 물어보는 동안 다른 경로가 먼저 끝낼 수 있다.
     */
    @Test
    @DisplayName("웹훅이 확정 중일 때 대사 배치가 같은 결제를 집어도 다시 지급하지 않는다")
    void concurrentWebhookAndReconcileProvisionsOnce() throws Exception {
        given(paymentGateway.findPaymentBySessionId(MERCHANT_UID)).willReturn(paidResult());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> webhook =
                    pool.submit(() -> catchThrowable(() -> service.applyWebhookEvent(paymentId, succeededWebhook())));
            assertThat(firstReachedOutbox.await(30, TimeUnit.SECONDS)).isTrue();

            // 대사 배치는 1단계에서 아직 PENDING 을 본다(웹훅이 커밋 전이므로). 그 상태로 아임포트에
            // 물어보고 paid 를 받는다. 낡은 판정으로 확정하면 여기서 구독이 하나 더 생긴다.
            Future<Boolean> reconcile = pool.submit(() -> service.reconcilePending(paymentId));

            assertThat(resultOrNullIfStillRunning(reconcile, 3)).isNull(); // 결제 행 락에서 대기

            releaseFirst.countDown();

            assertThat(webhook.get(30, TimeUnit.SECONDS)).isNull();
            // 락을 잡고 다시 보니 이미 SUCCEEDED → 대사는 손대지 않고 물러난다
            assertThat(reconcile.get(30, TimeUnit.SECONDS)).isFalse();

            assertThat(subscriptionRepository.count()).isEqualTo(1);
            assertThat(outboxEventRepository.count()).isEqualTo(1);
            assertThat(provisioningCalls.get()).isEqualTo(1);
        } finally {
            releaseFirst.countDown();
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("확정이 끝난 뒤 클라 확정이 다시 와도 조용히 성공한다 - 락이 정상 재요청을 막지 않는다")
    void repeatedClientConfirmAfterCommitIsNoOp() {
        releaseFirst.countDown(); // 붙잡지 않는다(순차 시나리오)

        service.completePayment(userId, paymentId, CLIENT_IMP_UID);
        service.completePayment(userId, paymentId, CLIENT_IMP_UID); // 이미 확정된 결제

        assertThat(subscriptionRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
        assertThat(provisioningCalls.get()).isEqualTo(1);
    }
}
