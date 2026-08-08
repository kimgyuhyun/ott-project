package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MembershipSubscriptionRepository 의 커스텀 JPQL 검증
 *
 * 왜 이 테스트가 필요한가
 * - findActiveEffectiveByUser 는 직접 작성한 JPQL 인데, 모든 서비스 테스트가 이 메서드를 목으로 대체한다.
 *   즉 쿼리 조건이 틀려도 서비스 테스트는 전부 통과한다. 실제 쿼리를 확인하는 곳이 여기뿐이다.
 * - 특히 endAt = null(무기한 구독)이 조회되는 동작은 3ea06b8(무기한 구독 플랜변경 NPE 수정)의 전제다.
 *   이 동작이 깨지면 무기한 구독자가 조용히 "구독 없음" 으로 취급된다.
 *
 * 쿼리가 지키려는 규칙
 * - 사용자 일치 AND 상태 일치 AND startAt <= now AND (endAt is null OR endAt >= now)
 *
 * 왜 실제 PostgreSQL 인가
 * - 직접 작성한 쿼리는 운영과 같은 DB 제품에서 확인해야 한다. NULL 정렬 순서와 limit 절 번역처럼
 *   여기서 검증하는 동작(endAt is null 포함, startAt desc 정렬)이 제품마다 다르므로,
 *   H2 에서 통과해도 운영 동작을 보장하지 못한다.
 *
 * Docker 가 없으면 컨테이너를 못 띄운다. Testcontainers 가 그 경우 조건부로 테스트를 건너뛴다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 컨테이너 URL 을 쓰기 위해 자동 대체를 끈다
@Import(JpaSliceTestSupport.class)
@Testcontainers(disabledWithoutDocker = true)
// 스키마는 Flyway 가 아니라 엔티티 기준으로 만든다. 이 테스트가 보는 것은 스키마 이력이 아니라 JPQL 이고,
// 같은 컨테이너 슬라이스인 MembershipSubscriptionLockTest·PaymentMerchantUidUniqueTest 와 구성을 맞춘다.
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true" // DDL 오류를 조용히 넘기지 않는다
})
class MembershipSubscriptionRepositoryTest {

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
    private TestEntityManager entityManager;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 17, 12, 0);

    private User persistUser(String email) {
        return entityManager.persist(User.createLocalUser(email, "encoded-password", "테스터"));
    }

    private MembershipPlan persistPlan(String name) {
        return entityManager.persist(
                MembershipPlan.createBasicPlan(name, "설명", new Money(9900L, "KRW"), 1));
    }

    /**
     * 구독을 저장한다. 정적 팩토리는 status 를 ACTIVE 로 고정하는데, 이 테스트는 조회 쿼리가 상태로
     * 거르는지를 보는 것이라 임의 상태가 필요하다. 도메인 전이 메서드는 상태마다 부수 필드(해지 시각,
     * 재시도 카운트)를 함께 바꿔 조회 조건과 무관한 값까지 끌고 들어오므로, 여기서는 필드만 주입한다.
     */
    private MembershipSubscription persistSubscription(User user, MembershipPlan plan,
                                                       LocalDateTime startAt, LocalDateTime endAt,
                                                       MembershipSubscriptionStatus status) {
        MembershipSubscription subscription =
                MembershipSubscription.createSubscription(user, plan, startAt, endAt);
        ReflectionTestUtils.setField(subscription, "status", status);
        return entityManager.persist(subscription);
    }

    @Test
    @DisplayName("기간 내 ACTIVE 구독은 조회된다")
    void findsActiveSubscriptionInsidePeriod() {
        User user = persistUser("active@example.com");
        MembershipPlan plan = persistPlan("Basic");
        MembershipSubscription subscription = persistSubscription(
                user, plan, NOW.minusDays(10), NOW.plusDays(20), MembershipSubscriptionStatus.ACTIVE);

        Optional<MembershipSubscription> found = subscriptionRepository.findActiveEffectiveByUser(
                user.getId(), MembershipSubscriptionStatus.ACTIVE, NOW);

        assertThat(found).contains(subscription);
    }

    @Test
    @DisplayName("종료일이 없는 무기한 구독도 조회된다 - 무기한 구독자가 '구독 없음' 으로 취급되면 안 된다")
    void findsOpenEndedSubscription() {
        User user = persistUser("openended@example.com");
        MembershipPlan plan = persistPlan("Open Ended");
        MembershipSubscription subscription = persistSubscription(
                user, plan, NOW.minusDays(10), null, MembershipSubscriptionStatus.ACTIVE);

        Optional<MembershipSubscription> found = subscriptionRepository.findActiveEffectiveByUser(
                user.getId(), MembershipSubscriptionStatus.ACTIVE, NOW);

        assertThat(found).contains(subscription);
    }

    @Test
    @DisplayName("종료일이 지난 구독은 제외된다")
    void excludesExpiredSubscription() {
        User user = persistUser("expired@example.com");
        MembershipPlan plan = persistPlan("Expired");
        persistSubscription(user, plan, NOW.minusDays(40), NOW.minusDays(1), MembershipSubscriptionStatus.ACTIVE);

        Optional<MembershipSubscription> found = subscriptionRepository.findActiveEffectiveByUser(
                user.getId(), MembershipSubscriptionStatus.ACTIVE, NOW);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("해지된 구독은 기간이 남아 있어도 ACTIVE 조회에서 제외된다")
    void excludesCanceledSubscription() {
        User user = persistUser("canceled@example.com");
        MembershipPlan plan = persistPlan("Canceled");
        persistSubscription(user, plan, NOW.minusDays(10), NOW.plusDays(20), MembershipSubscriptionStatus.CANCELED);

        Optional<MembershipSubscription> found = subscriptionRepository.findActiveEffectiveByUser(
                user.getId(), MembershipSubscriptionStatus.ACTIVE, NOW);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("시작일이 미래인 구독은 아직 조회되지 않는다")
    void excludesNotYetStartedSubscription() {
        User user = persistUser("future@example.com");
        MembershipPlan plan = persistPlan("Future");
        persistSubscription(user, plan, NOW.plusDays(1), NOW.plusDays(30), MembershipSubscriptionStatus.ACTIVE);

        Optional<MembershipSubscription> found = subscriptionRepository.findActiveEffectiveByUser(
                user.getId(), MembershipSubscriptionStatus.ACTIVE, NOW);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자의 구독은 조회되지 않는다")
    void excludesOtherUsersSubscription() {
        User owner = persistUser("owner@example.com");
        User other = persistUser("other@example.com");
        MembershipPlan plan = persistPlan("Shared");
        persistSubscription(owner, plan, NOW.minusDays(10), NOW.plusDays(20), MembershipSubscriptionStatus.ACTIVE);

        Optional<MembershipSubscription> found = subscriptionRepository.findActiveEffectiveByUser(
                other.getId(), MembershipSubscriptionStatus.ACTIVE, NOW);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("유효한 구독이 둘이면 startAt 이 최신인 것을 반환한다")
    void returnsMostRecentlyStartedSubscription() {
        User user = persistUser("two@example.com");
        MembershipPlan oldPlan = persistPlan("Old Plan");
        MembershipPlan newPlan = persistPlan("New Plan");
        persistSubscription(user, oldPlan, NOW.minusDays(30), NOW.plusDays(10), MembershipSubscriptionStatus.ACTIVE);
        MembershipSubscription newer = persistSubscription(
                user, newPlan, NOW.minusDays(1), NOW.plusDays(30), MembershipSubscriptionStatus.ACTIVE);

        Optional<MembershipSubscription> found = subscriptionRepository.findActiveEffectiveByUser(
                user.getId(), MembershipSubscriptionStatus.ACTIVE, NOW);

        assertThat(found).contains(newer);
    }

    @Test
    @DisplayName("findTopByUser_IdOrderByStartAtDesc 는 상태와 무관하게 가장 최근 구독을 반환한다")
    void findsMostRecentSubscriptionRegardlessOfStatus() {
        User user = persistUser("recent@example.com");
        MembershipPlan plan = persistPlan("Recent");
        persistSubscription(user, plan, NOW.minusDays(30), NOW.minusDays(20), MembershipSubscriptionStatus.EXPIRED);
        MembershipSubscription newest = persistSubscription(
                user, plan, NOW.minusDays(5), null, MembershipSubscriptionStatus.CANCELED);

        Optional<MembershipSubscription> found =
                subscriptionRepository.findTopByUser_IdOrderByStartAtDesc(user.getId());

        assertThat(found).contains(newest);
    }

    /**
     * findAllByUserAndStatusIn 은 탈퇴 시 정리 대상을 고르는 쿼리다. 최신 한 건이 아니라 전부를 돌려줘야 한다.
     * 한 건이라도 남으면 autoRenew=true 인 채로 살아남아, 정기결제 배치가 익명화된 계정에 계속 청구한다.
     */
    @Test
    @DisplayName("findAllByUserAndStatusIn 은 겹치는 ACTIVE 와 PAST_DUE 를 모두 반환한다 - 한 건만 끊으면 청구가 남는다")
    void findsEverySubscriptionInGivenStatuses() {
        User user = persistUser("overlap@example.com");
        MembershipPlan plan = persistPlan("Overlap");
        // 무기한 구독이 있는 사용자가 재구독하면 실제로 만들어지는 조합이다
        MembershipSubscription openEnded = persistSubscription(
                user, plan, NOW.minusDays(60), null, MembershipSubscriptionStatus.ACTIVE);
        MembershipSubscription active = persistSubscription(
                user, plan, NOW.minusDays(5), NOW.plusDays(25), MembershipSubscriptionStatus.ACTIVE);
        MembershipSubscription pastDue = persistSubscription(
                user, plan, NOW.minusDays(30), NOW.minusDays(1), MembershipSubscriptionStatus.PAST_DUE);

        List<MembershipSubscription> found = subscriptionRepository.findAllByUserAndStatusIn(
                user.getId(),
                List.of(MembershipSubscriptionStatus.ACTIVE, MembershipSubscriptionStatus.PAST_DUE));

        // 만료된 PAST_DUE 도 포함돼야 한다. 던닝 재시도는 endAt 이 지났는지와 무관하게 돈다
        assertThat(found).containsExactlyInAnyOrder(openEnded, active, pastDue);
    }

    @Test
    @DisplayName("findAllByUserAndStatusIn 은 요청한 상태가 아니거나 다른 사용자의 구독은 제외한다")
    void excludesOtherStatusesAndUsers() {
        User user = persistUser("target@example.com");
        User other = persistUser("bystander@example.com");
        MembershipPlan plan = persistPlan("Mixed");
        MembershipSubscription active = persistSubscription(
                user, plan, NOW.minusDays(5), NOW.plusDays(25), MembershipSubscriptionStatus.ACTIVE);
        persistSubscription(user, plan, NOW.minusDays(60), NOW.minusDays(30), MembershipSubscriptionStatus.EXPIRED);
        persistSubscription(user, plan, NOW.minusDays(20), NOW.plusDays(10), MembershipSubscriptionStatus.CANCELED);
        persistSubscription(other, plan, NOW.minusDays(5), NOW.plusDays(25), MembershipSubscriptionStatus.ACTIVE);

        List<MembershipSubscription> found = subscriptionRepository.findAllByUserAndStatusIn(
                user.getId(),
                List.of(MembershipSubscriptionStatus.ACTIVE, MembershipSubscriptionStatus.PAST_DUE));

        assertThat(found).containsExactly(active);
    }
}
