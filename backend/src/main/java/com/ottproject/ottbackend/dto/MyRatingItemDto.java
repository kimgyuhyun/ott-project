package com.ottproject.ottbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 내 별점 목록 아이템 DTO
 * - 작품 ID/타이틀/포스터/내 점수/수정일시 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyRatingItemDto {
    private Long animeId;
    private String title;
    private String posterUrl;
    private Double score;
    private LocalDateTime updatedAt;
}


