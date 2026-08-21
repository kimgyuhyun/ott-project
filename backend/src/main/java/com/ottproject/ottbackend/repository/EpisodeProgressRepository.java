package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.EpisodeProgress;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * EpisodeProgressRepository
 *
 * 큰 흐름
 * - 사용자×에피소드 진행률을 조회하는 JPA 리포지토리.
 * - 단건/벌크 조회 파생 메서드를 제공한다.
 *
 * 읽기 전용인 이유
 * - episode_progress 쓰기는 MyBatis(PlayerProgressQueryMapper) 한 수단으로만 한다(ARCHITECTURE 3).
 *   JpaRepository 를 상속하면 save/delete 가 다시 노출되어 쓰기 경로가 갈라지므로, 조회 메서드만
 *   선언하는 Repository 마커를 상속한다. 읽기 경로가 MyBatis 와 섞이는 것은 규칙이 허용한다.
 *
 * 메서드 개요
 * - findByUser_IdAndEpisode_Id: 사용자×에피소드 단건 조회
 * - findByUser_IdAndEpisode_IdIn: 여러 에피소드 진행률 벌크 조회
 */
public interface EpisodeProgressRepository extends Repository<EpisodeProgress, Long> { // 진행률
    Optional<EpisodeProgress> findByUser_IdAndEpisode_Id(Long userId, Long episodeId); // 단건

    List<EpisodeProgress> findByUser_IdAndEpisode_IdIn(Long userId, Collection<Long> episodeIds); // 벌크

    // 마이페이지용 시청 기록 목록 조회 (최근 시청 순)
    List<EpisodeProgress> findByUser_IdOrderByUpdatedAtDesc(Long userId);

    // 마이페이지용 시청 기록 목록 조회 (페이지네이션 지원)
    org.springframework.data.domain.Page<EpisodeProgress> findByUser_IdOrderByUpdatedAtDesc(
            Long userId, org.springframework.data.domain.Pageable pageable);

    // 마이페이지용 시청 기록 목록 조회 (90일 제한, 페이지네이션 지원)
    org.springframework.data.domain.Page<EpisodeProgress> findByUser_IdAndUpdatedAtAfterOrderByUpdatedAtDesc(
            Long userId, LocalDateTime dateAfter, org.springframework.data.domain.Pageable pageable);
}
