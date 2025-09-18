package com.ottproject.ottbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.dto.NotificationDto;
import com.ottproject.ottbackend.dto.NotificationDataDto;
import com.ottproject.ottbackend.entity.Notification;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.entity.UserSettings;
import com.ottproject.ottbackend.enums.NotificationType;
import com.ottproject.ottbackend.repository.NotificationRepository;
import com.ottproject.ottbackend.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * NotificationService
 *
 * 큰 흐름
 * - 알림 생성, 조회, 읽음 처리 등의 비즈니스 로직을 처리한다.
 * - 사용자 설정에 따라 알림 생성 여부를 결정한다.
 *
 * 메서드 개요
 * - createEpisodeUpdateNotification: 에피소드 업데이트 알림 생성
 * - createCommentActivityNotification: 댓글 활동 알림 생성
 * - getNotifications: 사용자 알림 목록 조회
 * - getUnreadCount: 읽지 않은 알림 개수 조회
 * - markAsRead: 알림 읽음 처리
 * - markAllAsRead: 전체 알림 읽음 처리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final ObjectMapper objectMapper;

    /**
     * 에피소드 업데이트 알림 생성
     * 
     * @param userId 알림 받을 사용자 ID
     * @param animeTitle 애니메이션 제목
     * @param episodeNumber 에피소드 번호
     * @param animeId 애니메이션 ID
     * @param episodeId 에피소드 ID
     */
    @Transactional
    public void createEpisodeUpdateNotification(Long userId, String animeTitle, int episodeNumber, Long animeId, Long episodeId) {
        // 사용자 설정 확인
        if (!isNotificationEnabled(userId, "workUpdates")) {
            log.debug("사용자 {}의 관심작품 업데이트 알림이 비활성화되어 있습니다.", userId);
            return;
        }

        // 중복 알림 확인
        String contentId = String.valueOf(episodeId);
        if (notificationRepository.countDuplicateNotifications(userId, NotificationType.EPISODE_UPDATE.name(), contentId) > 0) {
            log.debug("사용자 {}에게 이미 에피소드 {} 업데이트 알림이 존재합니다.", userId, episodeId);
            return;
        }

        User user = new User();
        user.setId(userId);
        
        Notification notification = Notification.createEpisodeUpdateNotification(
                user, animeTitle, episodeNumber, animeId, episodeId);
        
        notificationRepository.save(notification);
        log.info("사용자 {}에게 에피소드 업데이트 알림 생성: {}", userId, animeTitle);
    }

    /**
     * 댓글 활동 알림 생성
     * 
     * @param userId 알림 받을 사용자 ID
     * @param actorName 활동한 사용자 이름
     * @param activityType 활동 타입
     * @param contentType 콘텐츠 타입
     * @param contentId 콘텐츠 ID
     * @param animeId 애니메이션 ID
     * @param episodeId 에피소드 ID (에피소드 댓글인 경우만)
     * @param commentContent 댓글 내용 (댓글인 경우만)
     */
    @Transactional
    public void createCommentActivityNotification(Long userId, String actorName, String activityType, 
            String contentType, Long contentId, Long animeId, Long episodeId, String commentContent) {
        
        log.info("🔔 [NOTIFICATION] 알림 생성 시작 - 사용자: {}, 활동자: {}, 타입: {}", userId, actorName, activityType);
        
        // 사용자 설정 확인
        if (!isNotificationEnabled(userId, "communityActivity")) {
            log.warn("🔔 [NOTIFICATION] 사용자 {}의 커뮤니티 활동 알림이 비활성화되어 있습니다.", userId);
            return;
        }
        log.info("🔔 [NOTIFICATION] 사용자 {}의 커뮤니티 활동 알림이 활성화되어 있습니다.", userId);

        // 중복 알림 확인
        String contentIdStr = String.valueOf(contentId);
        long duplicateCount = notificationRepository.countDuplicateNotifications(userId, NotificationType.COMMENT_ACTIVITY.name(), contentIdStr);
        if (duplicateCount > 0) {
            log.warn("🔔 [NOTIFICATION] 사용자 {}에게 이미 콘텐츠 {} 활동 알림이 존재합니다. (중복 개수: {})", userId, contentId, duplicateCount);
            return;
        }
        log.info("🔔 [NOTIFICATION] 중복 알림 없음 - 사용자: {}, 콘텐츠: {}", userId, contentId);

        User user = new User();
        user.setId(userId);
        
        Notification notification = Notification.createCommentActivityNotification(
                user, actorName, activityType, contentType, contentId, animeId, episodeId, commentContent);
        
        Notification savedNotification = notificationRepository.save(notification);
        log.info("🔔 [NOTIFICATION] 알림 생성 완료 - ID: {}, 사용자: {}, 활동자: {}, 타입: {}", 
                savedNotification.getId(), userId, actorName, activityType);
    }

    /**
     * 사용자 알림 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<NotificationDto> getNotifications(Long userId, Pageable pageable) {
        Page<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        
        return notifications.map(this::convertToDto);
    }

    /**
     * 읽지 않은 알림 개수 조회
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    /**
     * 알림 읽음 처리
     */
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Optional<Notification> notificationOpt = notificationRepository.findById(notificationId);
        
        if (notificationOpt.isPresent()) {
            Notification notification = notificationOpt.get();
            
            // 본인의 알림인지 확인
            if (!notification.getUser().getId().equals(userId)) {
                throw new IllegalArgumentException("본인의 알림만 읽음 처리할 수 있습니다.");
            }
            
            notification.markAsRead();
            notificationRepository.save(notification);
            log.debug("알림 {} 읽음 처리 완료", notificationId);
        }
    }

    /**
     * 전체 알림 읽음 처리
     */
    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> unreadNotifications = notificationRepository
                .findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false, Pageable.unpaged())
                .getContent();
        
        unreadNotifications.forEach(Notification::markAsRead);
        notificationRepository.saveAll(unreadNotifications);
        
        log.info("사용자 {}의 전체 알림 읽음 처리 완료: {}개", userId, unreadNotifications.size());
    }

    /**
     * 사용자 알림 설정 확인
     */
    private boolean isNotificationEnabled(Long userId, String settingType) {
        log.info("🔔 [NOTIFICATION] 사용자 {}의 {} 알림 설정 확인 중...", userId, settingType);
        
        Optional<UserSettings> settingsOpt = userSettingsRepository.findByUserId(userId);
        
        if (settingsOpt.isEmpty()) {
            log.info("🔔 [NOTIFICATION] 사용자 {}의 설정이 없음 - 기본값으로 활성화", userId);
            return true; // 설정이 없으면 기본값으로 활성화
        }
        
        UserSettings settings = settingsOpt.get();
        boolean isEnabled = false;
        
        switch (settingType) {
            case "workUpdates":
                isEnabled = settings.getNotificationWorkUpdates() != null ? settings.getNotificationWorkUpdates() : true;
                log.info("🔔 [NOTIFICATION] 사용자 {}의 관심작품 업데이트 알림: {} (설정값: {})", 
                        userId, isEnabled, settings.getNotificationWorkUpdates());
                break;
            case "communityActivity":
                isEnabled = settings.getNotificationCommunityActivity() != null ? settings.getNotificationCommunityActivity() : true;
                log.info("🔔 [NOTIFICATION] 사용자 {}의 커뮤니티 활동 알림: {} (설정값: {})", 
                        userId, isEnabled, settings.getNotificationCommunityActivity());
                break;
            default:
                isEnabled = true;
                log.info("🔔 [NOTIFICATION] 사용자 {}의 알림 설정 타입 {} - 기본값으로 활성화", userId, settingType);
                break;
        }
        
        return isEnabled;
    }

    /**
     * Notification 엔티티를 DTO로 변환
     */
    private NotificationDto convertToDto(Notification notification) {
        NotificationDataDto dataDto = parseNotificationData(notification.getData());
        
        return NotificationDto.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .content(notification.getContent())
                .data(dataDto)
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    /**
     * JSON 문자열을 NotificationDataDto로 파싱
     */
    private NotificationDataDto parseNotificationData(String dataJson) {
        if (dataJson == null || dataJson.trim().isEmpty()) {
            return NotificationDataDto.builder().build();
        }
        
        try {
            return objectMapper.readValue(dataJson, NotificationDataDto.class);
        } catch (JsonProcessingException e) {
            log.warn("알림 데이터 파싱 실패: {}", dataJson, e);
            return NotificationDataDto.builder().build();
        }
    }
}
