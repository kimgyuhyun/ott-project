package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Notification;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
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
// JpaAuditingConfig 를 일부러 싣지 않는다. Notification 은 @CreatedDate(nullable=false)인데 팩토리가
// 값을 넣지 않아 Auditing 을 켜고 싶어지지만, @EnableJpaAuditing 은 AspectJ 로 위빙된
// AnnotationBeanConfigurerAspect(JVM 전역 싱글턴)에 이 컨텍스트의 BeanFactory 를 심어 놓는다.
// 테스트 컨텍스트는 캐싱되어 닫히지 않으므로 그 참조가 계속 살아, 이후 실행되는 '다른' 슬라이스에서
// AuditingEntityListener 가 남의 BeanFactory 로부터 핸들러를 주입받아 타임스탬프를 덮어쓴다.
// (실제로 UserRepositorySignupCountTest 가 실행 순서에 따라 깨졌다)
// 그래서 여기서는 Auditing 대신 픽스처가 createdAt 을 직접 채운다 — 이 테스트는 시각을 검증하지 않는다.
@DataJpaTest
@Import(JpaSliceTestSupport.class)
// Flyway 마이그레이션은 PostgreSQL 전용이라 H2 슬라이스에서 돌리면 깨진다.
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 17, 12, 0);

    private User user;

    @BeforeEach
    void setUp() {
        user = entityManager.persist(User.createLocalUser("notify@example.com", "password", "수신자"));
    }

    private long countCommentActivityDuplicates(Long contentId) {
        return countCommentActivityDuplicates(contentId, "COMMENT_LIKE");
    }

    private long countCommentActivityDuplicates(Long contentId, String activityType) {
        return notificationRepository.countDuplicateNotifications(
                user.getId(), NotificationType.COMMENT_ACTIVITY.name(), String.valueOf(contentId), activityType);
    }

    /**
     * 알림을 저장한다. createdAt 은 @CreatedDate 지만 Auditing 을 싣지 않으므로(클래스 주석 참고)
     * not-null 을 만족시키려면 직접 채워야 한다. 이 테스트는 시각을 검증하지 않아 고정값으로 충분하다.
     */
    private Notification persist(Notification notification) {
        notification.setCreatedAt(NOW);
        return entityManager.persist(notification);
    }

    private Notification persistCommentActivity(Long contentId) {
        return persist(Notification.createCommentActivityNotification(
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
        persist(Notification.createCommentActivityNotification(
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

    /**
     * 좋아요와 대댓글은 서로 다른 사건이다. 안 읽은 좋아요 알림이 대댓글 알림을 막으면
     * 사용자는 자기 댓글에 달린 답글을 영영 모른다.
     */
    @Test
    @DisplayName("활동 타입이 다르면 같은 댓글이어도 중복이 아니다 - 좋아요가 대댓글 알림을 막으면 안 된다")
    void doesNotTreatDifferentActivityTypesAsDuplicate() {
        persistCommentActivity(123L); // COMMENT_LIKE, 안 읽음

        assertThat(countCommentActivityDuplicates(123L, "COMMENT_REPLY")).isZero();
    }

    @Test
    @DisplayName("활동 타입이 같으면 기존대로 중복으로 센다")
    void stillCountsDuplicateForSameActivityType() {
        persistCommentActivity(123L); // COMMENT_LIKE, 안 읽음

        assertThat(countCommentActivityDuplicates(123L, "COMMENT_LIKE")).isEqualTo(1);
    }

    @Test
    @DisplayName("활동 타입이 같아도 콘텐츠가 다르면 중복이 아니다")
    void activityTypeMatchStillRequiresSameContent() {
        persistCommentActivity(123L);

        assertThat(countCommentActivityDuplicates(456L, "COMMENT_LIKE")).isZero();
    }

    @Test
    @DisplayName("에피소드 알림도 중복 검사가 걸린다 - data 에 contentId 가 없어 늘 0 이던 회귀")
    void countsDuplicateForEpisodeUpdate() {
        persist(Notification.createEpisodeUpdateNotification(user, "제목", 3, 7L, 42L));

        long count = notificationRepository.countDuplicateNotifications(
                user.getId(), NotificationType.EPISODE_UPDATE.name(), "42");

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("에피소드 알림도 접두어만 같은 ID 에는 걸리지 않는다")
    void episodeUpdateDoesNotMatchOnIdPrefix() {
        persist(Notification.createEpisodeUpdateNotification(user, "제목", 3, 7L, 42L));

        long count = notificationRepository.countDuplicateNotifications(
                user.getId(), NotificationType.EPISODE_UPDATE.name(), "4");

        assertThat(count).isZero();
    }
}
