package com.ottproject.ottbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * RecentAnimeWatchDto
 *
 * 큰 흐름
 * - 마이페이지 최근 본 목록에서 애니메이션별 최신 1건의 시청 기록을 표현하는 DTO.
 *
 * 필드 개요
 * - animeId: 애니메이션 ID
 * - episodeId: 최신 시청 에피소드 ID
 * - episodeNumber: 최신 시청 에피소드 번호
 * - positionSec: 시청 위치(초)
 * - durationSec: 총 길이(초)
 * - updatedAt: 마지막 시청 시각
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentAnimeWatchDto {
    private Long animeId;
    private Long episodeId;
    private Integer episodeNumber;
    private Integer positionSec;
    private Integer durationSec;
    private LocalDateTime updatedAt;
}


