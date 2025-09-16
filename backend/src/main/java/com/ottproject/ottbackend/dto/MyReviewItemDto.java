package com.ottproject.ottbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 내 리뷰 목록 아이템 DTO
 * - 리뷰 ID/작품 정보/본문 요약/작성/수정일시 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyReviewItemDto {
    private Long reviewId;
    private Long animeId;
    private String title;
    private String posterUrl;
    private String content;
    private Double score; // 내 별점(존재 시)
    private Long likeCount; // 좋아요 개수
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


