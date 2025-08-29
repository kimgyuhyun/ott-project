package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Subtitle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * SubtitleRepository
 *
 * 큰 흐름
 * - 에피소드별 자막 정보를 조회하는 JPA 리포지토리
 *
 * 메서드 개요
 * - findByEpisodeId: 에피소드별 자막 목록 조회
 * - findByEpisodeIdAndLanguage: 특정 언어 자막 조회
 * - findByEpisodeIdAndIsDefaultTrue: 기본 자막 조회
 */
@Repository
public interface SubtitleRepository extends JpaRepository<Subtitle, Long> {
    List<Subtitle> findByEpisodeId(Long episodeId);
    Optional<Subtitle> findByEpisodeIdAndLanguage(Long episodeId, String language);
    Optional<Subtitle> findByEpisodeIdAndIsDefaultTrue(Long episodeId);
}
