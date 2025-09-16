package com.ottproject.ottbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 마이페이지 집계 DTO
 * - 별점/리뷰/댓글 개수 반환
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MypageStatsDto {
    private long ratingCount; // 별점 개수
    private long reviewCount;
    private long commentCount;
}


