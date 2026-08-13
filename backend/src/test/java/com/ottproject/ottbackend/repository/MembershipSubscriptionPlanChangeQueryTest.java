package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PlanChangeType;
import com.ottproject.ottbackend.mybatis.MembershipSubscriptionQueryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * findSubscriptionsWithScheduledPlanChanges 의 대상 선정 검증 (실제 PostgreSQL, 실제 매퍼 XML)
 *
 * 왜 이 테스트가 필요한가
 * - 이 SQL 은 6시간마다 도는 배치가 "플랜을 갈아끼울 구독"을 고르는 자리다. RecurringBillingServiceTest 는
 *   이 매퍼를 목으로 대체하므로 WHERE 조건이 틀려도 전부 통과한다.
 * - 실제로 상태 조건이 빠져 있어서, 해지된 구독과 탈퇴로 익명화된 계정의 구독까지 플랜이 바뀌고
 *   변경 안내 메일이 발송됐다. 목으로는 잡히지 않는 종류의 결함이라 실제 DB 에 걸어 확인한다.
 *
 * 쿼리가 지키려는 규칙
 * - 상태 일치 AND autoRenew AND NOT cancelAtPeriodEnd AND nextBillingAt 도래 AND nextPlan 있음
 *
 * Docker 가 없으면 컨테이너를 못 띄운다. Testcontainers 가 그 경우 조건부로 테스트를 건너뛴다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 컨테이너 URL 을 쓰기 위해 자동 대체를 끈다
@Import(MyBatisSliceTestSupport.class) // 껍데기가 아니라 실제로 매퍼 XML 을 물린 SqlSessionFactory
@Testcontainers(disabledWithoutDocker = true)
@Tag("testcontainers") // testFast 가 제외하는 태그. 컨테이너를 띄우는 값이 비싸서 편집 직후 되먹임용 실행에서는 뺀다.
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create", // create-drop 이 아니다: 종료 시 drop DDL 이 이미 내려간 컨테이너에 붙으려다 30초를 버린다
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
})
class MembershipSubscriptionPlanChangeQueryTest {

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
    private MembershipSubscriptionQueryMapper queryMapper;

    @Autowired
    private TestEntityManager entityManager;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 9, 12, 0);
    private static final List<String> TARGET_STATUSES = List.of(
            MembershipSubscriptionStatus.ACTIVE.name(), MembershipSubscriptionStatus.PAST_DUE.name());

    private MembershipPlan persistPlan(String name) {
        return entityManager.persist(
                MembershipPlan.createBasicPlan(name, "설명", new Money(9900L, "KRW"), 1));
    }

    /**
     * 플랜 변경이 예약된 구독을 만든다. 상태와 해지 플래그는 이 쿼리가 걸러야 할 대상이라 직접 주입한다
     * (도메인 전이 메서드는 부수 필드까지 함께 바꿔 검증 대상이 흐려진다).
     */
    private MembershipSubscription persistScheduled(String email, MembershipPlan plan, MembershipPlan nextPlan,
                                                    MembershipSubscriptionStatus status,
                                                    boolean autoRenew, boolean cancelAtPeriodEnd,
                                                    LocalDateTime nextBillingAt) {
        User user = entityManager.persist(User.createLocalUser(email, "encoded-password", "테스터"));
        MembershipSubscription sub = MembershipSubscription.createSubscription(
                user, plan, NOW.minusDays(30), NOW.plusDays(1));
        if (nextPlan != null) {
            sub.schedulePlanChange(nextPlan, nextBillingAt, PlanChangeType.DOWNGRADE);
        }
        sub.scheduleNextBillingAt(nextBillingAt);
        ReflectionTestUtils.setField(sub, "status", status);
        ReflectionTestUtils.setField(sub, "autoRenew", autoRenew);
        ReflectionTestUtils.setField(sub, "cancelAtPeriodEnd", cancelAtPeriodEnd);
        return entityManager.persist(sub);
    }

    @Test
    @DisplayName("해지된 구독과 말일 해지 예약 구독은 제외한다 - 끝난 구독의 플랜을 바꾸고 안내 메일을 보내면 안 된다")
    void excludesCanceledAndScheduledToEndSubscriptions() {
        MembershipPlan plan = persistPlan("Basic");
        MembershipPlan nextPlan = persistPlan("Lite");

        MembershipSubscription eligible = persistScheduled("eligible@example.com", plan, nextPlan,
                MembershipSubscriptionStatus.ACTIVE, true, false, NOW.minusMinutes(1));
        // 탈퇴/환불로 즉시 해지된 구독. 예약을 지우지 않던 시절의 데이터도 여기 걸린다
        persistScheduled("canceled@example.com", plan, nextPlan,
                MembershipSubscriptionStatus.CANCELED, false, false, NOW.minusMinutes(1));
        // 말일 해지 예약. 만료로 끝나므로 플랜을 바꿀 이유가 없다(재개하면 다시 대상이 된다)
        persistScheduled("atperiodend@example.com", plan, nextPlan,
                MembershipSubscriptionStatus.ACTIVE, false, true, NOW.minusMinutes(1));
        entityManager.flush();

        List<MembershipSubscription> found =
                queryMapper.findSubscriptionsWithScheduledPlanChanges(TARGET_STATUSES, NOW);

        assertThat(found).extracting(MembershipSubscription::getId).containsExactly(eligible.getId());
    }

    @Test
    @DisplayName("다음 청구 시각이 아직 안 됐거나 플랜 변경 예약이 없으면 제외한다")
    void excludesNotDueAndUnscheduledSubscriptions() {
        MembershipPlan plan = persistPlan("Basic");
        MembershipPlan nextPlan = persistPlan("Lite");

        MembershipSubscription due = persistScheduled("due@example.com", plan, nextPlan,
                MembershipSubscriptionStatus.ACTIVE, true, false, NOW.minusMinutes(1));
        persistScheduled("notdue@example.com", plan, nextPlan,
                MembershipSubscriptionStatus.ACTIVE, true, false, NOW.plusDays(3));
        persistScheduled("unscheduled@example.com", plan, null,
                MembershipSubscriptionStatus.ACTIVE, true, false, NOW.minusMinutes(1));
        entityManager.flush();

        List<MembershipSubscription> found =
                queryMapper.findSubscriptionsWithScheduledPlanChanges(TARGET_STATUSES, NOW);

        assertThat(found).extracting(MembershipSubscription::getId).containsExactly(due.getId());
    }
}
