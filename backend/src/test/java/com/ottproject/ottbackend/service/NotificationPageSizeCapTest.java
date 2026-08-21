package com.ottproject.ottbackend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.dto.NotificationDto;
import com.ottproject.ottbackend.entity.Notification;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.repository.JpaSliceTestSupport;
import com.ottproject.ottbackend.util.PageLimitUtil;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 목록 API 페이지 크기 상한이 실제 SQL 까지 먹는지 검증 (실제 PostgreSQL)
 *
 * 왜 이 테스트가 필요한가
 * - size 는 보정 없이 그대로 SQL LIMIT 으로 내려간다. 상한이 없으면 size=100000 요청 하나로
 *   테이블을 통째로 읽는다. 이 프로젝트의 목록 API 18곳이 전부 그 상태였다.
 * - 상한을 넣었다는 것과 상한이 쿼리까지 도달한다는 것은 다른 말이다. 목으로 검증하면
 *   PageRequest 를 만들어 넘겼다는 사실만 확인되고, 정작 DB 가 몇 행을 돌려주는지는 모른다.
 *
 * 왜 H2 가 아니라 Testcontainers 인가
 * - 실제로 확인하려는 값이 "PostgreSQL 이 돌려준 행 수"라서다.
 *
 * 대표로 알림 목록을 고른 이유
 * - 상한 로직은 PageLimitUtil 한 곳에 있고 18개 경로가 같은 함수를 부른다. 그중 JPA 로 도는
 *   경로 하나를 실제 DB 에 태우면 "상한값이 LIMIT 이 된다"는 것이 확인된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 컨테이너 URL 을 쓰기 위해 자동 대체를 끈다
@Import({JpaSliceTestSupport.class, NotificationService.class, NotificationPageSizeCapTest.ObjectMapperTestConfig.class
})
@Testcontainers(disabledWithoutDocker = true)
@Tag("testcontainers") // testFast 가 제외하는 태그. 컨테이너를 띄우는 값이 비싸서 편집 직후 되먹임용 실행에서는 뺀다.
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create" // create-drop 이 아니다: 종료 시 drop DDL 이 이미 내려간 컨테이너에 붙으려다 30초를 버린다
        })
class NotificationPageSizeCapTest {

    /** 상한(100)보다 확실히 많은 행을 넣어야 잘리는지 알 수 있다. */
    private static final int SEEDED_ROWS = 150;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 12, 0);

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

    /** NotificationService 의 생성자 의존성이라 빈이 필요하다(알림 data JSON 직렬화용). */
    @TestConfiguration
    static class ObjectMapperTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private TestEntityManager entityManager;

    private Long userId;

    @BeforeEach
    void setUp() {
        User user = entityManager.persist(User.createLocalUser("cap@example.com", "password", "수신자"));
        userId = user.getId();

        for (int i = 0; i < SEEDED_ROWS; i++) {
            // createdAt 은 @CreatedDate 지만 이 슬라이스는 Auditing 을 싣지 않는다(JpaSliceTestSupport 주석 참고).
            // not-null 을 만족시키려면 픽스처가 직접 채워야 한다.
            Notification notification = Notification.createEpisodeUpdateNotification(user, "제목", i, 7L, (long) i);
            notification.setCreatedAt(NOW.plusSeconds(i));
            entityManager.persist(notification);
        }
        entityManager.flush();
    }

    @Test
    @DisplayName("size 를 상한보다 크게 요청해도 상한만큼만 돌아온다")
    void oversizedRequestIsCapped() {
        Page<NotificationDto> page = notificationService.getNotifications(userId, PageRequest.of(0, 100_000));

        assertThat(page.getContent()).hasSize(PageLimitUtil.MAX_PAGE_SIZE);
        assertThat(page.getSize()).isEqualTo(PageLimitUtil.MAX_PAGE_SIZE);
        // 전체 건수는 그대로 보고된다 — 잘린 것은 한 페이지 크기지 조회 대상이 아니다.
        assertThat(page.getTotalElements()).isEqualTo(SEEDED_ROWS);
    }

    @Test
    @DisplayName("상한 이하 요청은 그대로 통과한다")
    void normalRequestIsUntouched() {
        Page<NotificationDto> page = notificationService.getNotifications(userId, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(20);
        assertThat(page.getSize()).isEqualTo(20);
    }
}
