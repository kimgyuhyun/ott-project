package com.ottproject.ottbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 자동완성 아이템 DTO
 * 자동완성 리스트 한 줄(작품) 단위를 나타냅니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchSuggestItemDto {
    private Long aniId; // 애니 ID
    private String title; // 애니 제목
    private String posterUrl; // 포스터 URL
    private Double rating; // 평점
    private Integer ratingCount; // 평점수
}
