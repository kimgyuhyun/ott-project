package com.ottproject.ottbackend.dto;

import com.ottproject.ottbackend.enums.AnimeStatus;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 마이페이지 찜 목록 항목 DTO
 * - 작품 기본 정보 + 찜한 시각
 */
@Getter
@Setter
@Builder
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
public class FavoriteAniDto { // 마이페이지 찜 목록 DTO
    private Long aniId; // 애니 ID
    private String title; // 제목
    private String posterUrl; // 포스터 URL
    private Double rating; // 평점
    private Integer ratingCount; // 평가 수
    private Boolean isDub; // 더빙
    private Boolean isSubtitle; // 자막
    private Boolean isExclusive; // 독점
    private Boolean isNew; // 신작
    private Boolean isPopular; // 인기
    private Boolean isCompleted; // 완결
    private AnimeStatus animeStatus; // 방영 상태
    private Integer year; // 연도
    private String type; // 타입
    private LocalDateTime favoritedAt; // 찜한 시각
}