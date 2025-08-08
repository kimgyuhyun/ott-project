package com.ottproject.ottbackend.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 에피소드 읽기용 DTO
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
