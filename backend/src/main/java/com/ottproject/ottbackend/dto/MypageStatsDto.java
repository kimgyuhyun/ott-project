package com.ottproject.ottbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 마이페이지 집계 DTO
 * - 보고싶다/리뷰/댓글 개수 반환
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MypageStatsDto {
    private long wantCount;
    private long reviewCount;
    private long commentCount;
}


