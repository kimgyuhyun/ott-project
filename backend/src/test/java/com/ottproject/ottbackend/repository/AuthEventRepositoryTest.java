package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.AuthEvent;
import com.ottproject.ottbackend.enums.AuthEventType;
import com.ottproject.ottbackend.enums.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuthEventRepository 의 일일 통계 집계 쿼리 검증
 *
 * 왜 이 테스트가 필요한가
 * - StatsSnapshotServiceTest 는 이 세 쿼리를 전부 목으로 대체한다. 즉 쿼리가 틀려도 서비스 테스트는 통과한다.
 *   같은 이유로 MembershipSubscriptionRepository 의 JPQL 이 limit 없이 방치돼 실장애가 됐었다.
 * - DAU 는 "로그인 성공 건수" 가 아니라 "고유 사용자 수" 이고, 비로그인 이벤트(userId null)를 빼야 한다.
 *   이 두 조건이 조용히 사라지면 DAU 가 부풀려지는데 스냅샷만 봐서는 알아채기 어렵다.
 * - 기간은 [start, end) 반열린 구간이다. 끝을 포함하면 자정 이벤트가 이틀에 중복 집계된다.
 */
@DataJpaTest
@Import(JpaSliceTestSupport.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuthEventRepositoryTest {

    @Autowired
    private AuthEventRepository authEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static final LocalDateTime START = LocalDateTime.of(2026, 7, 16, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 7, 17, 0, 0);

    /**
     * AuthEvent.of 는 발생 시각을 현재로 고정하므로, 경계를 보려면 저장 전에 시각을 덮어써야 한다.
     */
    private void persistEvent(Long userId, AuthEventType type, LocalDateTime occurredAt) {
        AuthEvent event = AuthEvent.of(
                userId, "user@example.com", type, AuthProvider.LOCAL, "127.0.0.1", "agent", "sess", null);
        event.setOccurredAt(occurredAt);
        entityManager.persist(event);
    }

    private long countLoginSuccess() {
        return authEventRepository.countByTypeBetween(AuthEventType.LOGIN_SUCCESS, START, END);
    }

    private long countDau() {
        return authEventRepository.countDistinctUsersByTypeBetween(AuthEventType.LOGIN_SUCCESS, START, END);
    }

    @Nested
    @DisplayName("countByTypeBetween")
    class CountByType {

        @Test
        @DisplayName("지정한 유형의 이벤트만 센다")
        void countsOnlyRequestedType() {
            persistEvent(1L, AuthEventType.LOGIN_SUCCESS, START.plusHours(1));
            persistEvent(1L, AuthEventType.LOGIN_FAIL, START.plusHours(2));
            persistEvent(1L, AuthEventType.LOGOUT, START.plusHours(3));

            assertThat(countLoginSuccess()).isEqualTo(1);
            assertThat(authEventRepository.countByTypeBetween(AuthEventType.LOGIN_FAIL, START, END)).isEqualTo(1);
        }

        @Test
        @DisplayName("기간 시작 시각의 이벤트는 포함한다")
        void includesEventAtStartBoundary() {
            persistEvent(1L, AuthEventType.LOGIN_SUCCESS, START);

            assertThat(countLoginSuccess()).isEqualTo(1);
        }

        @Test
        @DisplayName("기간 종료 시각의 이벤트는 제외한다 - 자정 이벤트가 이틀에 중복 집계되면 안 된다")
        void excludesEventAtEndBoundary() {
            persistEvent(1L, AuthEventType.LOGIN_SUCCESS, END);

            assertThat(countLoginSuccess()).isZero();
        }

        @Test
        @DisplayName("기간 밖의 이벤트는 세지 않는다")
        void excludesEventsOutsideRange() {
            persistEvent(1L, AuthEventType.LOGIN_SUCCESS, START.minusSeconds(1));
            persistEvent(1L, AuthEventType.LOGIN_SUCCESS, END.plusSeconds(1));

            assertThat(countLoginSuccess()).isZero();
        }

        @Test
        @DisplayName("같은 사용자가 여러 번 로그인하면 건수는 그만큼 센다")
        void countsEveryOccurrence() {
            persistEvent(1L, AuthEventType.LOGIN_SUCCESS, START.plusHours(1));
            persistEvent(1L, AuthEventType.LOGIN_SUCCESS, START.plusHours(2));
            persistEvent(1L, AuthEventType.LOGIN_SUCCESS, START.plusHours(3));

            assertThat(countLoginSuccess()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("countDistinctUsersByTypeBetween - DAU")
    class CountDistinctUsers {

        @Test
        @DisplayName("한 사람이 여러 번 로그인해도 하루 한 명으로 센다")
        void countsRepeatLoginsAsOneUser() {
            persistEvent(1L, AuthEventType.LOGIN_SUCCESS, START.plusHours(1));
            persistEvent(1L, AuthEventType.LOGIN_SUCCESS, START.plusHours(5));
            persistEvent(1L, AuthEventType.LOGIN_SUCCESS, START.plusHours(9));

            assertThat(countDau()).isEqualTo(1);
            assertThat(countLoginSuccess()).isEqualTo(3); // 건수와 다르다는 점이 핵심
        }

        @Test
        @DisplayName("서로 다른 사용자는 각각 센다")
        void countsDistinctUsersSeparately() {
            persistEvent(1L, AuthEventType.LOGIN_SUCCESS, START.plusHours(1));
            persistEvent(2L, AuthEventType.LOGIN_SUCCESS, START.plusHours(2));
            persistEvent(3L, AuthEventType.LOGIN_SUCCESS, START.plusHours(3));

            assertThat(countDau()).isEqualTo(3);
        }

        @Test
        @DisplayName("사용자를 식별하지 못한 이벤트는 DAU 에서 뺀다 - null 이 한 명으로 잡히면 안 된다")
        void excludesEventsWithoutUserId() {
            persistEvent(null, AuthEventType.LOGIN_SUCCESS, START.plusHours(1));
            persistEvent(null, AuthEventType.LOGIN_SUCCESS, START.plusHours(2));

            assertThat(countDau()).isZero();
        }

        @Test
        @DisplayName("식별된 사용자와 미식별 이벤트가 섞이면 식별된 쪽만 센다")
        void countsOnlyIdentifiedUsers() {
            persistEvent(1L, AuthEventType.LOGIN_SUCCESS, START.plusHours(1));
            persistEvent(null, AuthEventType.LOGIN_SUCCESS, START.plusHours(2));

            assertThat(countDau()).isEqualTo(1);
        }

        @Test
        @DisplayName("로그인 실패만 한 사용자는 DAU 에 들어가지 않는다")
        void excludesOtherEventTypes() {
            persistEvent(1L, AuthEventType.LOGIN_FAIL, START.plusHours(1));

            assertThat(countDau()).isZero();
        }

        @Test
        @DisplayName("DAU 도 기간 종료 시각은 제외한다")
        void excludesEventAtEndBoundary() {
            persistEvent(1L, AuthEventType.LOGIN_SUCCESS, END);

            assertThat(countDau()).isZero();
        }
    }
}
