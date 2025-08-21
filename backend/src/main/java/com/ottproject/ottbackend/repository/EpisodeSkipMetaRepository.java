package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.EpisodeSkipMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * EpisodeSkipMetaRepository
 *
 * 큰 흐름
 * - 회차별 스킵 구간 메타(인트로/엔딩)를 관리하는 JPA 리포지토리.
 *
 * 메서드 개요
 * - findByEpisodeId: 에피소드 ID로 1:1 메타 조회
 */
@Repository
public interface EpisodeSkipMetaRepository extends JpaRepository<EpisodeSkipMeta, Long> { // 스킵 메타
	Optional<EpisodeSkipMeta> findByEpisodeId(Long episodeId); // 1:1
}