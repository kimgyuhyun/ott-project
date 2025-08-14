package com.ottproject.ottbackend.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 진행률 응답 DTO
 * - 진행 위치/총 길이/갱신 시각
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeProgressResponseDto { // 진행률 공용 DTO(요청/응답 공통 필드)
	private Integer positionSec; // 현재 위치(초)
	private Integer durationSec; // 총 길이(초)
	private LocalDateTime updatedAt; // 응답 시 사용, 요청 시 null 허용
}


