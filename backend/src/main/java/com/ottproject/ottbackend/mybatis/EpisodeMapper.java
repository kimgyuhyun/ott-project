package com.ottproject.ottbackend.mybatis;

import com.ottproject.ottbackend.dto.EpisodeDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * EpisodeMapper
 *
 * 큰 흐름
 * - 에피소드 조회 전용 MyBatis 매퍼
 * - PlaybackAuthService에서 사용하는 단건 조회 메서드 제공
 *
 * 메서드 개요
 * - findEpisodeById: ID로 에피소드 조회 (비디오 스트림용)
 * - findNextEpisode: 다음 에피소드 조회
 */
@Mapper
public interface EpisodeMapper {

    /**
     * ID로 에피소드 조회 (비디오 스트림용)
     * @param id 에피소드 ID
     * @return 에피소드 정보 (없으면 null)
     */
    EpisodeDto findEpisodeById(@Param("id") Long id);

    /**
     * 다음 에피소드 조회
     * @param animeId 애니메이션 ID
     * @param episodeNumber 현재 에피소드 번호
     * @return 다음 에피소드 정보 (없으면 null)
     */
    EpisodeDto findNextEpisode(@Param("animeId") Long animeId, @Param("episodeNumber") Integer episodeNumber);

    /**
     * 애니메이션 ID로 에피소드 목록 조회
     * @param animeId 애니메이션 ID
     * @return 에피소드 목록
     */
    List<EpisodeDto> findEpisodesByAnimeId(@Param("animeId") Long animeId);
}
