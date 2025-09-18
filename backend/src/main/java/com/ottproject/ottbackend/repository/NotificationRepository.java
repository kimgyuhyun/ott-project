package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Notification;
import com.ottproject.ottbackend.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * NotificationRepository
 *
 * 큰 흐름
 * - 알림 CRUD 및 조회용 파생 메서드를 제공하는 JPA 리포지토리.
 *
 * 메서드 개요
 * - findByUserId: 사용자별 알림 목록 조회 (페이징)
 * - countUnreadByUserId: 사용자별 읽지 않은 알림 개수
 * - findByUserIdAndType: 사용자별 특정 타입 알림 조회
 * - findUnreadByUserId: 사용자별 읽지 않은 알림 조회
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    /**
     * 사용자별 알림 목록 조회 (최신순, 페이징)
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    /**
     * 사용자별 읽지 않은 알림 개수 조회
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.isRead = false")
    long countUnreadByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자별 특정 타입 알림 조회
     */
    Page<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(
            Long userId, NotificationType type, Pageable pageable);
    
    /**
     * 사용자별 읽지 않은 알림 조회
     */
    Page<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(
            Long userId, Boolean isRead, Pageable pageable);
    
    /**
     * 특정 기간 이전의 읽은 알림 조회 (아카이빙용)
     */
    @Query("SELECT n FROM Notification n WHERE n.isRead = true AND n.createdAt < :cutoffDate")
    Page<Notification> findReadNotificationsBefore(@Param("cutoffDate") LocalDateTime cutoffDate, Pageable pageable);
    
    /**
     * 사용자별 특정 콘텐츠 관련 알림 중복 확인
     */
    @Query(value = "SELECT COUNT(n) FROM notifications n WHERE n.user_id = :userId AND n.type = :type AND n.data LIKE CONCAT('%\"contentId\":', :contentId, '%') AND n.is_read = false", nativeQuery = true)
    long countDuplicateNotifications(@Param("userId") Long userId, @Param("type") String type, @Param("contentId") String contentId);
}
