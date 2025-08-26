package com.ottproject.ottbackend.dto;

import lombok.*;

/**
 * 댓글 응답 DTO
 *
 * 큰 흐름
 * - 리뷰 댓글을 플랫하게 렌더링하기 위한 표시 정보를 담는다.
 *
 * 필드 개요
 * - id/reviewId/parentId: 식별/대상/부모
 * - userId/userName/content/commentStatus: 작성자/본문/상태
 * - replacesCount/likeCount/isLikedByCurrentUser: 수/좋아요/현재 사용자 좋아요 여부
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCommentsResponseDto {

	private Long id; // 댓글 ID
	private Long reviewId; // 대상 리뷰 ID
	private Long parentId; // 부모 댓글 ID(최상위면 null)

	private Long userId; // 작성자 ID
	private String userName; // 작성자 이름/닉네임
	private String userProfileImage; // 작성자 프로필 이미지 URL

	private String content; // 댓글 내용
	private com.ottproject.ottbackend.enums.CommentStatus commentStatus; // 댓글 상태

	private Integer replacesCount; // 대댓글 개수(옵션)
	private Integer likeCount; // 댓글 좋아요 개수
	private Boolean isLikedByCurrentUser; // 현재 사용자 좋아요 여부
}
