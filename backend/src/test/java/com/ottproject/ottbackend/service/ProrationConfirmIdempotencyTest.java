package com.ottproject.ottbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.repository.JpaSliceTestSupport;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.OutboxEventRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;

/**
 * 차액 결제 확정의 이중 확정 방어 검증 (실제 PostgreSQL, 실제 커밋, 실제 스레드 2개)
 *
 * 왜 이 테스트가 필요한가
 * - completeProrationPayment 는 락 없이 읽은 상태로 "PENDING 인가"를 판정한 뒤 PG 재검증(HTTP)을
 *   부르고 확정했다. 판정과 확정 사이가 PortOne 응답 시간만큼 벌어져 있어 동시 요청 둘 다 통과했다.
 * - 이 경로에는 백업 안전망이 없다는 점이 일반 결제와 다르다. 웹훅과 대사 배치는 merchant_uid 의
 *   "proration_" 접두사를 보고 이 결제를 의도적으로 건너뛰므로, 클라이언트 확정이 유일한 경로다.
 *   즉 여기서 새면 다른 데서 막아주지 않는다.
 * - 증상은 아웃박스 2건 → 영수증 메일 2통이다. 플랜은 같은 값으로 두 번 바뀌어 결과가 같으므로
 *   구독만 보면 아무 문제 없어 보이고, 그래서 조용히 지나간다.
 * - ProrationPaymentServiceTest 는 차액 계산(순수 함수)만 본다. 두 번째 요청이 첫 요청의 커밋까지
 *   실제로 대기하는지는 그 테스트로 증명되지 않는다(ARCHITECTURE 15절).
 *
 * 테스트 환경에 대한 메모(ARCHITECTURE 15절과의 차이)
 * - 15절은 @SpringBootTest + Testcontainers 를 요구하지만 이 프로젝트에는 @SpringBootTest 가 하나도
 *   없어 전체 컨텍스트를 띄우면 Redis/Kafka/RabbitMQ/메일까지 붙는다. 결함이 서비스-DB 경계에 있으므로
 *   PaymentConfirmIdempotencyTest 와 같은 슬라이스 구성을 따랐다.
 *   핵심 요구(실제 커밋, 실제 스레드 2개, 테스트 메서드에 @Transactional 없음)는 그대로 지킨다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 컨테이너 URL 을 쓰기 위해 자동 대체를 끈다
@Import({JpaSliceTestSupport.class, ProrationPaymentService.class})
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED) // @DataJpaTest 의 감싸는 트랜잭션을 끈다
class ProrationConfirmIdempotencyTest {

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
     * 확정 트랜잭션 안에서 불리는 유일한 협력자라, 아래에서 이 빈을 붙잡아 락 구간을 재현한다.
     */
    @TestConfiguration
    static class ObjectMapperTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }
    }

    private static final String MERCHANT_UID = "proration_confirm1"; // 차액 결제의 merchant_uid
    private static final String FIRST_IMP_UID = "imp_first"; // 먼저 도착한 확정의 외부 결제 ID
    private static final String SECOND_IMP_UID = "imp_second"; // 뒤에 도착한 확정의 외부 결제 ID

    @Autowired
    private ProrationPaymentService service;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MembershipSubscriptionRepository subscriptionRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipPlanRepository planRepository;

    @MockitoBean
    private PaymentGateway paymentGateway;

    // 확정 트랜잭션 안(락 보유 중)에 붙잡을 지점. 실제 직렬화는 그대로 수행해야 아웃박스가 정상 적재된다.
    @MockitoSpyBean
    private ObjectMapper objectMapper;

    private Long userId;
    private Long paymentId;
    private Long targetPlanId;

    /** 첫 확정이 아웃박스 적재 직전(락 보유, 커밋 전)에 도달했음을 알린다 */
    private CountDownLatch firstReachedOutbox;
    /** 첫 확정을 커밋시키는 신호 */
    private CountDownLatch releaseFirst;
    /** 확정 마무리 단계에 몇 번 들어왔는가 = 영수증 메일이 몇 통 나가는가 */
    private AtomicInteger outboxSerializations;

    @BeforeEach
    void setUp() throws Exception { // writeValueAsString 스터빙이 검사 예외를 선언한다
        outboxEventRepository.deleteAll();
        paymentRepository.deleteAll();
        subscriptionRepository.deleteAll();
        planRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(User.createLocalUser("proration@example.com", "encoded", "테스터"));
        MembershipPlan basic = planRepository.save(
                MembershipPlan.createBasicPlan("Basic", "기본", new Money(9900L, "KRW"), 1));
        MembershipPlan premium = planRepository.save(
                MembershipPlan.createBasicPlan("Premium", "프리미엄", new Money(14900L, "KRW"), 1));
        userId = user.getId();
        targetPlanId = premium.getId();

        LocalDateTime now = LocalDateTime.now();
        subscriptionRepository.save(MembershipSubscription.createSubscription(
                user, basic, now.minusDays(5), now.plusDays(25))); // ACTIVE + 잔여기간

        Payment payment = Payment.createPendingPayment(
                user, premium, PaymentProvider.IMPORT, MERCHANT_UID, new Money(4980L, "KRW"));
        payment.attachMetadata("{\"type\":\"proration\",\"currentPlanCode\":\"" + basic.getCode()
                + "\",\"targetPlanCode\":\"" + premium.getCode() + "\"}");
        ReflectionTestUtils.setField(payment, "createdAt", now); // 슬라이스에는 Auditing 이 없어 직접 채운다
        ReflectionTestUtils.setField(payment, "updatedAt", now);
        paymentId = paymentRepository.save(payment).getId();

        firstReachedOutbox = new CountDownLatch(1);
        releaseFirst = new CountDownLatch(1);
        outboxSerializations = new AtomicInteger();

        // PG 재검증은 통과시킨다(이 테스트의 관심사가 아니다).
        given(paymentGateway.verifyPayment(anyString(), anyString(), anyLong())).willReturn(true);

        // 첫 확정을 아웃박스 적재 직전에 붙잡아 둔다.
        // 이 지점은 결제 행을 잠그고 SUCCEEDED 로 바꾼 뒤, 아직 커밋하기 전이다.
        doAnswer(invocation -> {
            if (outboxSerializations.incrementAndGet() == 1) {
                firstReachedOutbox.countDown();
                releaseFirst.await(10, TimeUnit.SECONDS);
            }
            return invocation.callRealMethod(); // 실제 직렬화는 그대로 수행
        }).when(objectMapper).writeValueAsString(any());
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
     * confirmProrationPayment 의 findByIdForUpdate 를 findById 로 되돌리면 여기서 잡힌다.
     */
    @Test
    @DisplayName("차액 결제를 동시에 확정하려 하면 한 번만 확정된다 - 영수증 메일 2통 차단")
    void concurrentProrationConfirmIsProcessedOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = pool.submit(() -> catchThrowable(
                    () -> service.completeProrationPayment(userId, paymentId, FIRST_IMP_UID)));
            assertThat(firstReachedOutbox.await(30, TimeUnit.SECONDS)).isTrue(); // 락을 쥐고 커밋 직전까지 옴

            // 두 번째 확정. 별도 스레드여야 별도 커넥션/트랜잭션이 열린다.
            // 결과를 문자열로 돌려주는 이유: 성공과 "아직 대기 중"이 둘 다 null 이면 구별되지 않아,
            // 락을 없애도 대기 단언이 통과해 버리는 가짜 테스트가 된다.
            Future<String> second = pool.submit(() -> {
                try {
                    service.completeProrationPayment(userId, paymentId, SECOND_IMP_UID);
                    return "confirmed"; // 두 번 확정됨 = 방어 실패
                } catch (ResponseStatusException e) {
                    return "rejected-" + e.getStatusCode().value();
                }
            });

            // 첫 확정이 커밋하기 전에는 결제 행 락에서 대기해야 한다.
            // 여기서 이미 끝나 있으면 락이 없다는 뜻이고, 그건 곧 낡은 PENDING 을 보고 또 확정했다는 뜻이다.
            assertThat(resultOrNullIfStillRunning(second, 3)).isNull();

            releaseFirst.countDown(); // 첫 확정 커밋

            assertThat(first.get(30, TimeUnit.SECONDS)).isNull(); // 첫 확정은 정상 완료
            // 기존 계약 유지: 이미 처리된 결제는 400(순차 재요청과 같은 응답)
            assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo("rejected-400");

            // 사용자에게 보이는 증상: 영수증 메일이 두 번 나가면 안 된다
            assertThat(outboxEventRepository.count()).isEqualTo(1);
            assertThat(outboxSerializations.get()).isEqualTo(1);

            Payment confirmed = paymentRepository.findById(paymentId).orElseThrow();
            assertThat(confirmed.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            // 먼저 확정한 쪽의 imp_uid 가 남아야 한다(뒤에 온 쪽이 덮어쓰면 확정 기록이 흔들린다)
            assertThat(confirmed.getProviderPaymentId()).isEqualTo(FIRST_IMP_UID);

            // 플랜 변경은 실제로 일어나야 한다(락이 정상 흐름을 잡아먹지 않았는지)
            assertThat(subscriptionRepository.findAll())
                    .singleElement()
                    .satisfies(sub -> assertThat(sub.getMembershipPlan().getId()).isEqualTo(targetPlanId));
        } finally {
            releaseFirst.countDown(); // 실패 경로에서도 첫 확정이 매달려 있지 않게 한다
            // 단언이 먼저 깨져도 남은 스레드가 다음 테스트의 픽스처 위에 커밋하지 않도록 여기서 끝을 본다.
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("확정이 끝난 뒤 같은 결제를 다시 확정하면 400 - 기존 동작을 바꾸지 않는다")
    void repeatedProrationConfirmIsRejected() {
        releaseFirst.countDown(); // 붙잡지 않는다(순차 시나리오)

        service.completeProrationPayment(userId, paymentId, FIRST_IMP_UID);

        Throwable thrown = catchThrowable(
                () -> service.completeProrationPayment(userId, paymentId, SECOND_IMP_UID));

        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode().value()).isEqualTo(400);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }
}
