package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.EpisodeProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * EpisodeProgressRepository
 *
 * 큰 흐름
 * - 사용자×에피소드 진행률을 저장/조회하는 JPA 리포지토리.
 * - 단건/벌크 조회 파생 메서드를 제공한다.
 *
 * 메서드 개요
 * - findByUser_IdAndEpisode_Id: 사용자×에피소드 단건 조회
 * - findByUser_IdAndEpisode_IdIn: 여러 에피소드 진행률 벌크 조회
 */
@Repository
public interface EpisodeProgressRepository extends JpaRepository<EpisodeProgress, Long> { // 진행률
	Optional<EpisodeProgress> findByUser_IdAndEpisode_Id(Long userId, Long episodeId); // 단건
	List<EpisodeProgress> findByUser_IdAndEpisode_IdIn(Long userId, Collection<Long> episodeIds); // 벌크
	
	// 마이페이지용 시청 기록 목록 조회 (최근 시청 순)
	List<EpisodeProgress> findByUser_IdOrderByUpdatedAtDesc(Long userId);
	
	// 마이페이지용 시청 기록 목록 조회 (페이지네이션 지원)
	org.springframework.data.domain.Page<EpisodeProgress> findByUser_IdOrderByUpdatedAtDesc(Long userId, org.springframework.data.domain.Pageable pageable);
}