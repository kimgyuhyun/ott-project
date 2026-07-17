package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.config.JpaAuditingConfig;
import com.ottproject.ottbackend.entity.Notification;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.NotificationType;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NotificationRepository 의 중복 알림 검사 쿼리 검증
 *
 * 왜 이 테스트가 필요한가
 * - countDuplicateNotifications 는 data(JSON TEXT)를 LIKE 로 훑는 네이티브 쿼리다.
 *   문자열 매칭이라 키가 없거나 경계가 없으면 조용히 틀린 답을 내는데, 아무도 실행해 본 적이 없었다.
 *
 * 이 자리에서 잡은 회귀 2건(방향이 서로 반대다)
 * - 에피소드 알림 data 에 contentId 키가 아예 없어서 중복 검사가 늘 0 → 같은 알림이 계속 쌓였다.
 * - 값 경계가 없어 "contentId":1 이 "contentId":123 에 매칭 → 엉뚱한 알림이 막혔다.
 */
@DataJpaTest
@Import({NotificationRepositoryTest.MyBatisStubConfig.class, JpaAuditingConfig.class})
// Flyway 마이그레이션은 PostgreSQL 전용이라 H2 슬라이스에서 돌리면 깨진다.
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class NotificationRepositoryTest {

    /**
     * 메인 클래스의 @MapperScan 이 JPA 슬라이스에서도 매퍼를 등록하려 해서 SqlSessionFactory 가 필요하다.
     * 쿼리를 실행하지 않는 껍데기로 충분하다.
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
    private NotificationRepository notificationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User user;

    @BeforeEach
    void setUp() {
        user = entityManager.persist(User.createLocalUser("notify@example.com", "password", "수신자"));
    }

    private long countCommentActivityDuplicates(Long contentId) {
        return notificationRepository.countDuplicateNotifications(
                user.getId(), NotificationType.COMMENT_ACTIVITY.name(), String.valueOf(contentId));
    }

    private Notification persistCommentActivity(Long contentId) {
        return entityManager.persist(Notification.createCommentActivityNotification(
                user, "활동자", "COMMENT_LIKE", "REVIEW_COMMENT", contentId, 7L, null, "댓글"));
    }

    @Test
    @DisplayName("같은 콘텐츠의 안 읽은 알림이 있으면 중복으로 센다")
    void countsDuplicateForSameContent() {
        persistCommentActivity(123L);

        assertThat(countCommentActivityDuplicates(123L)).isEqualTo(1);
    }

    @Test
    @DisplayName("contentId 가 접두어만 같은 알림은 중복이 아니다 - 123 이 1 을 막으면 안 된다")
    void doesNotMatchOnIdPrefix() {
        persistCommentActivity(123L);

        assertThat(countCommentActivityDuplicates(1L)).isZero();
        assertThat(countCommentActivityDuplicates(12L)).isZero();
    }

    @Test
    @DisplayName("이미 읽은 알림은 중복으로 세지 않는다 - 다시 알릴 수 있어야 한다")
    void ignoresReadNotifications() {
        Notification notification = persistCommentActivity(123L);
        notification.markAsRead();
        entityManager.flush();

        assertThat(countCommentActivityDuplicates(123L)).isZero();
    }

    @Test
    @DisplayName("다른 사용자의 알림은 중복으로 세지 않는다")
    void ignoresOtherUsersNotifications() {
        User other = entityManager.persist(User.createLocalUser("other@example.com", "password", "타인"));
        entityManager.persist(Notification.createCommentActivityNotification(
                other, "활동자", "COMMENT_LIKE", "REVIEW_COMMENT", 123L, 7L, null, "댓글"));

        assertThat(countCommentActivityDuplicates(123L)).isZero();
    }

    @Test
    @DisplayName("알림 타입이 다르면 중복으로 세지 않는다")
    void ignoresOtherNotificationTypes() {
        persistCommentActivity(123L);

        long count = notificationRepository.countDuplicateNotifications(
                user.getId(), NotificationType.EPISODE_UPDATE.name(), "123");

        assertThat(count).isZero();
    }

    @Test
    @DisplayName("에피소드 알림도 중복 검사가 걸린다 - data 에 contentId 가 없어 늘 0 이던 회귀")
    void countsDuplicateForEpisodeUpdate() {
        entityManager.persist(Notification.createEpisodeUpdateNotification(user, "제목", 3, 7L, 42L));

        long count = notificationRepository.countDuplicateNotifications(
                user.getId(), NotificationType.EPISODE_UPDATE.name(), "42");

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("에피소드 알림도 접두어만 같은 ID 에는 걸리지 않는다")
    void episodeUpdateDoesNotMatchOnIdPrefix() {
        entityManager.persist(Notification.createEpisodeUpdateNotification(user, "제목", 3, 7L, 42L));

        long count = notificationRepository.countDuplicateNotifications(
                user.getId(), NotificationType.EPISODE_UPDATE.name(), "4");

        assertThat(count).isZero();
    }
}
