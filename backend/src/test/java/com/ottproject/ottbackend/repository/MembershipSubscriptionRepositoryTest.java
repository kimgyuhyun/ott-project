package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
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
 */
@DataJpaTest
@Import(MembershipSubscriptionRepositoryTest.MyBatisStubConfig.class)
// Flyway 마이그레이션은 PostgreSQL 전용 문법이라 H2 슬라이스에서 실행되면 깨진다.
// 이 테스트는 JPQL 만 보므로 Hibernate 가 엔티티에서 만든 스키마로 충분하다.
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MembershipSubscriptionRepositoryTest {

    /**
     * 메인 클래스의 @MapperScan("...mybatis") 이 JPA 슬라이스에서도 매퍼 빈을 등록하려 하는데,
     * @DataJpaTest 에는 MyBatis 자동설정이 없어 SqlSessionFactory 부재로 컨텍스트가 깨진다.
     * 이 테스트는 JPA 쿼리만 보므로 쿼리를 실행하지 않는 껍데기 팩토리를 넣어준다.
     * (AdminAuthorizationTest 의 같은 이름 설정과 동일한 우회 — 그쪽은 package-private 이라 재사용 불가)
     */
    @TestConfiguration
    static class MyBatisStubConfig {
        @Bean
        SqlSessionFactory sqlSessionFactory() {
            org.apache.ibatis.session.Configuration cfg = new org.apache.ibatis.session.Configuration();
            cfg.setEnvironment(new Environment("test", new JdbcTransactionFactory(), new SimpleDriverDataSource()));
            return new DefaultSqlSessionFactory(cfg);
        }
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
     * 구독을 저장한다. 정적 팩토리는 status 를 ACTIVE 로 고정하므로 다른 상태가 필요하면 세터로 덮어쓴다.
     */
    private MembershipSubscription persistSubscription(User user, MembershipPlan plan,
                                                       LocalDateTime startAt, LocalDateTime endAt,
                                                       MembershipSubscriptionStatus status) {
        MembershipSubscription subscription =
                MembershipSubscription.createSubscription(user, plan, startAt, endAt);
        subscription.setStatus(status);
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
}
