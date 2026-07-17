package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserRepository.countSignupsBetween 검증 (일일 통계의 가입자 수)
 *
 * 왜 이 테스트가 필요한가
 * - StatsSnapshotServiceTest 가 목으로 대체하는 커스텀 JPQL 이라 실제로 실행된 적이 없다.
 * - 기간은 [start, end) 반열린 구간이어야 한다. 끝을 포함하면 자정에 가입한 사용자가 이틀 모두에 잡힌다.
 *
 * 이 슬라이스에는 @EnableJpaAuditing 을 싣지 않는다. 실으면 @CreatedDate 가 저장 시점 현재 시각으로
 * 덮어써서 경계에 놓은 createdAt 이 사라진다(팩토리가 넣은 값이 그대로 남아야 경계를 검증할 수 있다).
 */
@DataJpaTest
@Import(JpaSliceTestSupport.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserRepositorySignupCountTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static final LocalDateTime START = LocalDateTime.of(2026, 7, 16, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 7, 17, 0, 0);

    private void persistUserSignedUpAt(String email, LocalDateTime createdAt) {
        User user = User.createLocalUser(email, "password", "가입자");
        user.setCreatedAt(createdAt);
        user.setUpdatedAt(createdAt);
        entityManager.persist(user);
    }

    private long countSignups() {
        return userRepository.countSignupsBetween(START, END);
    }

    @Test
    @DisplayName("기간 내에 가입한 사용자를 센다")
    void countsSignupsInRange() {
        persistUserSignedUpAt("a@example.com", START.plusHours(3));
        persistUserSignedUpAt("b@example.com", START.plusHours(9));

        assertThat(countSignups()).isEqualTo(2);
    }

    @Test
    @DisplayName("기간 시작 시각에 가입했으면 포함한다")
    void includesSignupAtStartBoundary() {
        persistUserSignedUpAt("a@example.com", START);

        assertThat(countSignups()).isEqualTo(1);
    }

    @Test
    @DisplayName("기간 종료 시각에 가입했으면 제외한다 - 자정 가입자가 이틀에 중복 집계되면 안 된다")
    void excludesSignupAtEndBoundary() {
        persistUserSignedUpAt("a@example.com", END);

        assertThat(countSignups()).isZero();
    }

    @Test
    @DisplayName("기간 밖에 가입한 사용자는 세지 않는다")
    void excludesSignupsOutsideRange() {
        persistUserSignedUpAt("a@example.com", START.minusSeconds(1));
        persistUserSignedUpAt("b@example.com", END.plusSeconds(1));

        assertThat(countSignups()).isZero();
    }

    @Test
    @DisplayName("가입자가 없는 날은 0 이다")
    void returnsZeroForQuietDay() {
        assertThat(countSignups()).isZero();
    }
}
