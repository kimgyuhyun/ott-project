package com.ottproject.ottbackend.enums;

/**
 * 알림 타입 Enum
 *
 * 큰 흐름
 * - 알림의 종류를 정의한다.
 * - 통합 타입으로 관리하여 확장성을 높인다.
 *
 * 타입 개요
 * - EPISODE_UPDATE: 관심작품 에피소드 업데이트
 * - COMMENT_ACTIVITY: 댓글 관련 모든 활동 (댓글, 대댓글, 좋아요)
 * - LIKE_ACTIVITY: 좋아요 관련 활동
 */
public enum NotificationType {
    EPISODE_UPDATE,      // 에피소드 업데이트 알림
    COMMENT_ACTIVITY,    // 댓글 활동 알림 (댓글, 대댓글, 좋아요)
    LIKE_ACTIVITY        // 좋아요 활동 알림
}
