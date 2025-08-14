package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.EpisodeProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 진행률 리포지토리
 * - 사용자/에피소드별 단건 및 벌크 조회 제공
 */
@Repository
public interface EpisodeProgressRepository extends JpaRepository<EpisodeProgress, Long> { // 진행률
	Optional<EpisodeProgress> findByUser_IdAndEpisode_Id(Long userId, Long episodeId); // 단건
	List<EpisodeProgress> findByUser_IdAndEpisode_IdIn(Long userId, Collection<Long> episodeIds); // 벌크
}