package com.ottproject.ottbackend.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 정주행 완료 작품 DTO
 *
 * 큰 흐름
 * - 정주행 완료한 작품의 기본 정보를 담는다.
 * - 마이페이지 정주행 탭에서 표시할 최소한의 정보만 포함한다.
 *
 * 필드 개요
 * - aniId/title/posterUrl: 식별/제목/포스터
 * - totalEpisodes/watchedEpisodes: 전체/시청 에피소드 수
 * - completedAt: 정주행 완료 시각
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BingeWatchDto {
    private Long aniId; // 애니 ID
    private String title; // 제목
    private String posterUrl; // 포스터 URL
    private Integer totalEpisodes; // 전체 에피소드 수
    private Integer watchedEpisodes; // 시청한 에피소드 수
    private LocalDateTime completedAt; // 정주행 완료 시각
}
