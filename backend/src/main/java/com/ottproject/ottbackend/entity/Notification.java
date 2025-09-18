package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 알림 엔티티
 *
 * 큰 흐름
 * - 사용자별 알림 정보를 저장한다.
 * - 통합 타입 + 메타데이터 방식으로 확장성을 높인다.
 *
 * 필드 개요
 * - id/user: 식별/소유자
 * - type/title/content: 알림 기본 정보
 * - data: JSON 메타데이터 (콘텐츠 ID, 타입 등)
 * - isRead/createdAt: 읽음 상태/생성 시각
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notifications_user_unread", columnList = "user_id, is_read, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 알림 고유 ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 알림 받을 사용자

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type; // 알림 타입

    @Column(nullable = false, length = 255)
    private String title; // 알림 제목

    @Column(columnDefinition = "TEXT")
    private String content; // 알림 내용

    @Column(columnDefinition = "TEXT")
    private String data; // JSON 메타데이터

    @Column(nullable = false)
    private Boolean isRead = false; // 읽음 여부 (기본값: false)

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt; // 생성 시각

    // ===== 정적 팩토리 메서드 =====

    /**
     * 에피소드 업데이트 알림 생성
     * 
     * @param user 알림 받을 사용자
     * @param animeTitle 애니메이션 제목
     * @param episodeNumber 에피소드 번호
     * @param animeId 애니메이션 ID
     * @param episodeId 에피소드 ID
     * @return 생성된 Notification 엔티티
     */
    public static Notification createEpisodeUpdateNotification(
            User user, String animeTitle, int episodeNumber, Long animeId, Long episodeId) {
        
        Notification notification = new Notification();
        notification.user = user;
        notification.type = NotificationType.EPISODE_UPDATE;
        notification.title = "새로운 에피소드 업데이트";
        notification.content = String.format("%s %d화가 업데이트되었습니다.", animeTitle, episodeNumber);
        notification.data = String.format(
            "{\"animeId\":%d,\"episodeId\":%d,\"animeTitle\":\"%s\",\"episodeNumber\":%d}",
            animeId, episodeId, animeTitle, episodeNumber
        );
        notification.isRead = false;
        
        return notification;
    }

    /**
     * 댓글 활동 알림 생성
     * 
     * @param user 알림 받을 사용자
     * @param actorName 활동한 사용자 이름
     * @param activityType 활동 타입 (COMMENT_LIKE, COMMENT_REPLY)
     * @param contentType 콘텐츠 타입 (REVIEW_COMMENT, EPISODE_COMMENT)
     * @param contentId 콘텐츠 ID
     * @param animeId 애니메이션 ID
     * @param episodeId 에피소드 ID (에피소드 댓글인 경우만)
     * @param commentContent 댓글 내용 (댓글인 경우만)
     * @return 생성된 Notification 엔티티
     */
    public static Notification createCommentActivityNotification(
            User user, String actorName, String activityType, String contentType, 
            Long contentId, Long animeId, Long episodeId, String commentContent) {
        
        Notification notification = new Notification();
        notification.user = user;
        notification.type = NotificationType.COMMENT_ACTIVITY;
        
        // 제목과 내용 설정
        if ("COMMENT_LIKE".equals(activityType)) {
            notification.title = "좋아요 알림";
            notification.content = String.format("%s님이 좋아요를 달았습니다.", actorName);
        } else if ("COMMENT_REPLY".equals(activityType)) {
            notification.title = "댓글 알림";
            notification.content = String.format("%s님이 댓글을 달았습니다.", actorName);
        }
        
        // 메타데이터 구성
        StringBuilder dataBuilder = new StringBuilder();
        dataBuilder.append("{");
        dataBuilder.append("\"activityType\":\"").append(activityType).append("\",");
        dataBuilder.append("\"contentType\":\"").append(contentType).append("\",");
        dataBuilder.append("\"contentId\":").append(contentId).append(",");
        dataBuilder.append("\"animeId\":").append(animeId);
        
        if (episodeId != null) {
            dataBuilder.append(",\"episodeId\":").append(episodeId);
        }
        
        if (commentContent != null) {
            dataBuilder.append(",\"commentContent\":\"").append(commentContent.replace("\"", "\\\"")).append("\"");
        }
        
        dataBuilder.append("}");
        notification.data = dataBuilder.toString();
        notification.isRead = false;
        
        return notification;
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 알림을 읽음 처리
     */
    public void markAsRead() {
        this.isRead = true;
    }

    /**
     * 메타데이터에서 특정 값 추출
     * 
     * @param key 추출할 키
     * @return 추출된 값 (문자열)
     */
    public String getDataValue(String key) {
        if (data == null || data.trim().isEmpty()) {
            return null;
        }
        
        // 간단한 JSON 파싱 (실제로는 Jackson ObjectMapper 사용 권장)
        String pattern = "\"" + key + "\":";
        int startIndex = data.indexOf(pattern);
        if (startIndex == -1) {
            return null;
        }
        
        startIndex += pattern.length();
        int endIndex = data.indexOf(",", startIndex);
        if (endIndex == -1) {
            endIndex = data.indexOf("}", startIndex);
        }
        
        if (endIndex == -1) {
            return null;
        }
        
        String value = data.substring(startIndex, endIndex).trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        
        return value;
    }
}
