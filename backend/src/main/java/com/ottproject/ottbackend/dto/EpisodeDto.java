package com.ottproject.ottbackend.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 에피소드 읽기용 DTO
 *
 * 큰 흐름
 * - 회차 카드/상세에서 필요한 필드만 노출한다.
 *
 * 필드 개요
 * - id/episodeNumber/title/thumbnailUrl/videoUrl: 식별/제목/미디어
 * - isActive/isReleased/createdAt/updatedAt: 운영/시각
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeDto {
    
    private Long id; // 에피소드 ID
    private Integer episodeNumber; // 화수(1,2,3)
    private String title; // 제목
    private String thumbnailUrl; // 썸네일
    private String videoUrl; // 영상 URL
    private Boolean isActive; // 활성 여부
    private Boolean isReleased; // 공개여부
    private LocalDateTime createdAt; // 생성일시
    private LocalDateTime updatedAt; // 수정일시
}
