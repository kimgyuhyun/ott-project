package com.ottproject.ottbackend.dto;

import com.ottproject.ottbackend.enums.ReviewStatus;
import lombok.*;

/**
 * 리뷰 응답 DTO
 *
 * 큰 흐름
 * - 리뷰 목록/상세에서 필요한 표시 정보를 담는다.
 *
 * 필드 개요
 * - id/aniId/userId/userName/content/rating/status
 * - likeCount/isLikedByCurrentUser
 */
@Getter
@Setter
@Builder
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
public class ReviewResponseDto {

    private Long id; // 리뷰 ID
    private Long aniId; // 대상 애니(목록) ID
    private Long userId; // 작성자 ID
    private String userName; // 작성자 이름/닉네임
    private String userProfileImage; // 작성자 프로필 이미지 URL
    private String content; // 리뷰 내용
    private Double rating; // 평점
    private ReviewStatus status; // 리뷰 상태

    private Integer likeCount; // 리뷰 좋아요 개수
    private Boolean isLikedByCurrentUser; // 현재 사용자 좋아요 여부
}
