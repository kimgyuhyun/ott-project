package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.EpisodeSkipMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 스킵 메타 리포지토리
 * - 에피소드별 1:1 메타 조회
 */
@Repository
public interface EpisodeSkipMetaRepository extends JpaRepository<EpisodeSkipMeta, Long> { // 스킵 메타
    Optional<EpisodeSkipMeta> findByEpisodeId(Long episodeId); // 1:1
}