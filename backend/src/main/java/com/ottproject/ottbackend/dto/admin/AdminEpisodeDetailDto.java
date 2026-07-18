package com.ottproject.ottbackend.dto.admin;

import com.ottproject.ottbackend.entity.Episode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 관리자 에피소드 등록 결과
 *
 * 필드 개요
 * - id/animeId: 생성된 에피소드와 소속 작품 식별자
 * - episodeNumber/title/thumbnailUrl/videoUrl: 저장된 값
 * - isReleased: 공개 여부(등록 직후에는 비공개)
 */
@Getter
@Builder
@AllArgsConstructor
public class AdminEpisodeDetailDto {

    private final Long id;
    private final Long animeId;
    private final Integer episodeNumber;
    private final String title;
    private final String thumbnailUrl;
    private final String videoUrl;
    private final Boolean isReleased;

    public static AdminEpisodeDetailDto from(Episode episode) {
        return AdminEpisodeDetailDto.builder()
                .id(episode.getId())
                .animeId(episode.getAnime().getId())
                .episodeNumber(episode.getEpisodeNumber())
                .title(episode.getTitle())
                .thumbnailUrl(episode.getThumbnailUrl())
                .videoUrl(episode.getVideoUrl())
                .isReleased(episode.getIsReleased())
                .build();
    }
}
