package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Episode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.lang.NonNull;

import java.util.Optional;

/**
 * EpisodeRepository
 *
 * 큰 흐름
 * - 에피소드 생성/수정/삭제 전용 JPA 리포지토리(목록/상세 조회는 MyBatis).
 * - 단건 편집 전 동시성 안전을 위해 비관적 락 기반 조회를 제공한다.
 *
 * 메서드 개요
 * - findById: 비관적 쓰기 락으로 단건 조회
 * - deleteByAnime_Id: 작품 기준 일괄 삭제(DML)
 * - findFirstByAnimeIdAndEpisodeNumberGreaterThanAndIsReleasedTrueOrderByEpisodeNumberAsc: 다음 화 조회(공개된 것만)
 */
@Repository // 스프링 빈 등록
public interface EpisodeRepository extends JpaRepository<Episode, Long> { // 에피소드 CUD

	@Lock(LockModeType.PESSIMISTIC_WRITE) // CUD 전에 레코드 쓰기 락
	@NonNull Optional<Episode> findById(@NonNull Long id); // 파생 메서드 + @Lock로 대체

	@Modifying(clearAutomatically = true, flushAutomatically = true) // DML + 동기화
	int deleteByAnime_Id(Long aniId); // 파생 삭제 메서드로 대체

	// 다음 화 조회(동일 Anime 기준, 공개된 것만)
	Episode findFirstByAnimeIdAndEpisodeNumberGreaterThanAndIsReleasedTrueOrderByEpisodeNumberAsc(Long aniId, Integer episodeNumber); // NEW
}
