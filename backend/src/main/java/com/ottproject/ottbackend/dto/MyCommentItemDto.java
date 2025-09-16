package com.ottproject.ottbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 내 댓글 목록 아이템 DTO
 * - 타겟 타입/ID, 작품 정보(가능 시), 본문 요약, 작성일시 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyCommentItemDto {
    private Long commentId;
    private String targetType; // REVIEW | EPISODE 등
    private Long targetId;
    private Long parentId; // null이면 최상위 댓글, 아니면 대댓글
    private Long animeId; // 조인 가능 시
    private String title; // 작품 타이틀 (가능 시)
    private String episodeTitle; // 에피소드 대상일 때 제목
    private String posterUrl; // 작품 포스터
    private String episodeThumbUrl; // 에피소드 대상일 때 썸네일
    private String content;
    private Long likeCount; // 좋아요 개수
    private String userProfileImage; // 작성자 프로필 이미지
    private LocalDateTime createdAt;
}


