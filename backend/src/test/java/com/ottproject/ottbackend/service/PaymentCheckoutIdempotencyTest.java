package com.ottproject.ottbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.dto.PaymentCheckoutCreateRequestDto;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.mybatis.PaymentQueryMapper;
import com.ottproject.ottbackend.repository.IdempotencyKeyRepository;
import com.ottproject.ottbackend.repository.JpaSliceTestSupport;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.mockito.Mockito.doAnswer;

/**
 * 체크아웃 생성의 멱등 판정 검증 (실제 PostgreSQL, 실제 커밋, 실제 스레드 2개)
 *
 * 왜 이 테스트가 필요한가
 * - 예전 checkout() 은 멱등키를 findByKeyValue 로 확인해 분기하고(select-then-insert), 플랜 조회·
 *   결제수단 등록·PG 세션 생성·Payment 생성을 다 끝낸 뒤 맨 마지막에 키를 저장했다.
 *   확인과 저장 사이가 그만큼 벌어져 있어 동시 요청 둘 다 확인을 통과했다.
 * - 결과는 PENDING 결제 2건과 PG 세션 2개다. payments.provider_session_id 에 유니크 제약이 있지만
 *   세션 ID 는 요청마다 새로 발급되므로 서로 다른 값이고, 따라서 아무것도 막지 못한다.
 * - 방금 고친 해지(membership.cancel)와 구조가 같지만 결론은 다르다. 해지는 중복을 성공으로 흡수하고,
 *   체크아웃은 원래부터 409 였으므로 동시 경합도 409 로 수렴시킨다(기존 계약 유지).
 *
 * 테스트 환경에 대한 메모(ARCHITECTURE 15절과의 차이)
 * - 15절은 @SpringBootTest + Testcontainers 를 요구하지만 이 프로젝트에는 @SpringBootTest 가 하나도 없어
 *   전체 컨텍스트를 띄우면 Redis/Kafka/RabbitMQ/메일까지 붙는다. 결함이 서비스-DB 경계에 있으므로
 *   MembershipCancelIdempotencyTest 와 같은 슬라이스 구성을 따랐다.
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
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true",
        // 아래 AuditTimestampInterceptor 주석 참고
        "spring.jpa.properties.hibernate.session_factory.interceptor="
                + "com.ottproject.ottbackend.service.PaymentCheckoutIdempotencyTest$AuditTimestampInterceptor"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED) // @DataJpaTest 의 감싸는 트랜잭션을 끈다
class PaymentCheckoutIdempotencyTest {

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
     * Payment.createdAt/updatedAt 은 @CreatedDate/@LastModifiedDate 이고 not-null 이다.
     *
     * 다른 슬라이스 테스트들은 픽스처가 ReflectionTestUtils 로 직접 채우지만, 이 테스트는 프로덕션
     * 코드(checkout)가 Payment 를 INSERT 하므로 그럴 수 없다. 그렇다고 @EnableJpaAuditing 을 켜면
     * JpaSliceTestSupport 가 경고한 JVM 전역 애스펙트가 딸려와 다른 슬라이스를 비결정적으로 오염시킨다.
     * 그래서 이 컨텍스트에만 붙는 Hibernate 인터셉터로 not-null 시각만 채운다.
     * 이 테스트는 시각 값 자체를 검증하지 않는다 — 삽입이 성립하게만 하는 장치다.
     */
    public static class AuditTimestampInterceptor implements Interceptor {
        @Override
        public boolean onSave(Object entity, Object id, Object[] state, String[] propertyNames, Type[] types) {
            boolean modified = false;
            for (int i = 0; i < propertyNames.length; i++) {
                if (state[i] == null
                        && ("createdAt".equals(propertyNames[i]) || "updatedAt".equals(propertyNames[i]))) {
                    state[i] = LocalDateTime.now();
                    modified = true;
                }
            }
            return modified;
        }
    }

    /** 체크아웃은 쓰지 않지만 PaymentCommandService 의 생성자 의존성이라 빈이 필요하다. */
    @TestConfiguration
    static class ObjectMapperTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    /** 같은 체크아웃 의도로 두 번 도착한 요청이 들고 오는 멱등키 */
    private static final String CHECKOUT_KEY = "checkout-key-1";

    @Autowired
    private PaymentCommandService service;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipPlanRepository planRepository;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @MockitoBean
    private PaymentQueryMapper paymentQueryMapper;

    @MockitoBean
    private PaymentMethodService paymentMethodService;

    @MockitoBean
    private PlayerProgressReadService playerProgressReadService;

    @MockitoBean
    private MembershipCommandService membershipCommandService;

    @MockitoBean
    private RecurringBillingService recurringBillingService;

    private Long userId;
    private String planCode;

    /** 첫 요청이 PG 세션 생성 지점에 도달했음을 알린다 */
    private CountDownLatch firstReachedGateway;
    /** 첫 요청을 커밋시키는 신호 */
    private CountDownLatch releaseFirst;
    /** PG 세션을 몇 개 만들었는가 = 사용자가 결제창을 몇 개 받는가 */
    private AtomicInteger sessionsCreated;

    @BeforeEach
    void setUp() {
        idempotencyKeyRepository.deleteAll();
        paymentRepository.deleteAll();
        planRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(User.createLocalUser("checkout@example.com", "encoded", "테스터"));
        MembershipPlan plan = planRepository.save(
                MembershipPlan.createBasicPlan("Basic", "설명", new Money(9900L, "KRW"), 1));
        userId = user.getId();
        planCode = plan.getCode();

        firstReachedGateway = new CountDownLatch(1);
        releaseFirst = new CountDownLatch(1);
        sessionsCreated = new AtomicInteger();

        // 첫 요청을 PG 세션 생성 지점에 붙잡아 둔다. 이 지점은 멱등키를 선삽입한 뒤, 아직 커밋하기 전이다.
        // 세션 ID 는 호출마다 다르게 발급한다(실제 PG 동작). 그래서 payments 의 유니크 제약은
        // 중복 체크아웃을 막아주지 못하고, 막는 것은 오직 멱등키뿐이다.
        doAnswer(invocation -> {
            int n = sessionsCreated.incrementAndGet();
            if (n == 1) {
                firstReachedGateway.countDown();
                releaseFirst.await(10, TimeUnit.SECONDS);
            }
            PaymentGateway.CheckoutSession session = new PaymentGateway.CheckoutSession();
            session.sessionId = "sess_checkout_" + n;
            return session;
        }).when(paymentGateway).createCheckoutSession(any(), any(), any(), any(), any(), anyLong());
    }

    private PaymentCheckoutCreateRequestDto checkoutReq(String idempotencyKey) {
        PaymentCheckoutCreateRequestDto req = new PaymentCheckoutCreateRequestDto();
        req.planCode = planCode;
        req.idempotencyKey = idempotencyKey;
        return req; // paymentService 는 비워 둔다(결제수단 등록 분기는 이 테스트의 관심사가 아니다)
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
     * 멱등 판정을 조회 후 분기로 되돌리거나 idempotency_keys 의 유니크 제약을 지우면 여기서 잡힌다.
     */
    @Test
    @DisplayName("같은 멱등키로 동시에 체크아웃 요청이 오면 하나만 생성된다 - 결제/PG세션 중복 생성 차단")
    void concurrentCheckoutWithSameKeyCreatesOne() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = pool.submit(
                    () -> catchThrowable(() -> service.checkout(userId, checkoutReq(CHECKOUT_KEY))));
            assertThat(firstReachedGateway.await(30, TimeUnit.SECONDS)).isTrue(); // 첫 요청이 키를 선점하고 커밋 직전까지 옴

            // 두 번째 요청. 별도 스레드여야 별도 커넥션/트랜잭션이 열린다.
            // 결과를 문자열로 돌려주는 이유: catchThrowable 로 감싸면 "성공"과 "아직 대기 중"이 둘 다
            // null 이라 구별되지 않는다. 제약을 없애도 대기 단언이 통과해 버리는 가짜 테스트가 된다.
            Future<String> second = pool.submit(() -> {
                try {
                    service.checkout(userId, checkoutReq(CHECKOUT_KEY));
                    return "created"; // 중복이 그대로 만들어짐 = 방어 실패
                } catch (ResponseStatusException e) {
                    return "rejected-" + e.getStatusCode().value();
                }
            });

            // 첫 요청이 커밋하기 전에는 유니크 인덱스에서 대기해야 한다.
            // 여기서 이미 끝나 있으면 제약이 판정하지 않았다는 뜻이다 = 둘 다 통과했다.
            assertThat(resultOrNullIfStillRunning(second, 3)).isNull();

            releaseFirst.countDown(); // 첫 요청 커밋

            assertThat(first.get(30, TimeUnit.SECONDS)).isNull(); // 첫 요청은 정상 완료
            // 기존 계약 유지: 중복 체크아웃은 409 다(해지처럼 성공으로 흡수하지 않는다)
            assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo("rejected-409");

            // 사용자에게 보이는 증상: PENDING 결제와 결제창이 두 개 생기면 안 된다
            assertThat(paymentRepository.count()).isEqualTo(1);
            assertThat(sessionsCreated.get()).isEqualTo(1);
            assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
        } finally {
            releaseFirst.countDown(); // 실패 경로에서도 첫 요청이 매달려 있지 않게 한다
            // 단언이 먼저 깨져도 남은 스레드가 다음 테스트의 픽스처 위에 커밋하지 않도록 여기서 끝을 본다.
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("같은 멱등키로 순차 재요청하면 409 - 기존 동작을 바꾸지 않는다")
    void sequentialCheckoutWithSameKeyIsRejected() {
        releaseFirst.countDown(); // 붙잡지 않는다(순차 시나리오)

        service.checkout(userId, checkoutReq(CHECKOUT_KEY));

        Throwable thrown = catchThrowable(() -> service.checkout(userId, checkoutReq(CHECKOUT_KEY)));

        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 멱등키는 막히지 않는다 - 선삽입이 정상 요청을 잡아먹지 않아야 한다")
    void differentKeysAreNotBlocked() {
        releaseFirst.countDown();

        service.checkout(userId, checkoutReq("checkout-key-a"));
        service.checkout(userId, checkoutReq("checkout-key-b"));

        assertThat(paymentRepository.count()).isEqualTo(2);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(2);
    }

    /**
     * 현재 프론트(useCheckout.ts / usePayment.ts)는 idempotencyKey 를 undefined 로 넘긴다.
     * 이 테스트는 그 상태의 의미를 못박는다: 키가 없으면 중복 방어는 0 이다.
     * 백엔드 수정만으로는 더블클릭 중복 체크아웃이 막히지 않는다는 뜻이므로, 프론트가 키를 보내기
     * 시작하면 이 테스트를 "막힌다"로 뒤집어야 한다.
     */
    @Test
    @DisplayName("멱등키가 없으면 중복 체크아웃이 그대로 두 건 생긴다 - 프론트가 키를 보내야 방어가 작동한다")
    void withoutKeyDuplicateCheckoutIsNotBlocked() {
        releaseFirst.countDown();

        service.checkout(userId, checkoutReq(null));
        service.checkout(userId, checkoutReq(null));

        assertThat(paymentRepository.count()).isEqualTo(2);
        assertThat(sessionsCreated.get()).isEqualTo(2);
        assertThat(idempotencyKeyRepository.count()).isZero();
    }
}
