package com.ottproject.ottbackend.dto;

import lombok.*;

/**
 * 알림 메타데이터 DTO
 *
 * 큰 흐름
 * - 알림의 상세 정보를 구조화된 형태로 제공한다.
 * - 알림 타입에 따라 다른 필드가 사용된다.
 *
 * 필드 개요
 * - 공통: animeId
 * - 에피소드 업데이트: episodeId, animeTitle, episodeNumber
 * - 댓글 활동: activityType, contentType, contentId, episodeId, commentContent
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDataDto {
    // 공통 필드
    private Long animeId; // 애니메이션 ID
    
    // 에피소드 업데이트 관련
    private Long episodeId; // 에피소드 ID
    private String animeTitle; // 애니메이션 제목
    private Integer episodeNumber; // 에피소드 번호
    
    // 댓글 활동 관련
    private String activityType; // 활동 타입 (COMMENT_LIKE, COMMENT_REPLY)
    private String contentType; // 콘텐츠 타입 (REVIEW_COMMENT, EPISODE_COMMENT)
    private Long contentId; // 콘텐츠 ID
    private String commentContent; // 댓글 내용
}
