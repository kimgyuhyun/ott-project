package com.ottproject.ottbackend.dto;

import com.ottproject.ottbackend.enums.CommentStatus;

/**
 * 댓글 응답 DTO
 * - 플랫 렌더링을 고려해 parentId 포함
 * replacesCount 는 프론트에서 접기/펼치기 등에 활용
 */
public class CommentResponseDto {

    private Long id; // 댓글 ID
    private Long reviewId; // 대상 리뷰 ID
    private Long parentId; // 부모 댓글 ID(최상위면 null)

    private Long userId; // 작성자 ID
    private String userName; // 작성자 이름/닉네임

    private String content; // 댓글 내용
    private CommentStatus commentStatus; // 댓글 상태

    private Integer replacesCount; // 대댓글 개수(옵션)
    private Integer likeCount; // 댓글 좋아요 개수
    private Boolean isLikedByCurrentUser; // 현재 사용자 좋아요 여부
}
