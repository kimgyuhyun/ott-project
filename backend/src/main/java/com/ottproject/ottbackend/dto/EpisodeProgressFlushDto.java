package com.ottproject.ottbackend.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 진행률 배치 반영 DTO
 *
 * 큰 흐름
 * - Redis 버퍼에서 꺼낸 진행률 한 건을 배치 upsert 파라미터로 옮긴다.
 * - 요청 처리 경로가 아니라 flush 스케줄러에서만 쓴다.
 *
 * 필드 개요
 * - userId/episodeId: upsert 충돌 키(유니크 제약과 동일)
 * - positionSec/durationSec: 반영할 값
 * - updatedAt: 버퍼에 기록된 시각. DB 값이 더 최신이면 덮어쓰지 않는 판단에 쓴다
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeProgressFlushDto {
    private Long userId;
    private Long episodeId;
    private Integer positionSec;
    private Integer durationSec;
    private LocalDateTime updatedAt;
}
