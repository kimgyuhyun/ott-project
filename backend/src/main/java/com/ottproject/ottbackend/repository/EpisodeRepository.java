package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Episode;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Episode CUD 전용 리포지토리
 * - 에피소드 생성/수정/삭제 전용 (목록/조회는 MyBatis)
 */
@Repository // 스프링 빈 등록
public interface EpisodeRepository extends JpaRepository<Episode, Long> { // 에피소드 CUD

    @Lock(LockModeType.PESSIMISTIC_WRITE) // CUD 전에 레코드 쓰기 락
    @Query("select e from Episode e where e.id = :id") // 잠금용 단건 조회
    Optional<Episode> findByIdForUpdate(@Param("id") Long id); // :id 바인딩

    @Modifying(clearAutomatically = true, flushAutomatically = true) // DML + 동기화
    @Query("delete from Episode e where e.animeDetail.id = :aniDetailId") // 상세에 속한 에피소드 전체 삭제
    int deleteByAniDetailId(@Param("aniDetailId") Long aniDetailId); // 삭제 행 수

    // 다음 화 조회(동일 AnimeDetail 기준, 공개된 것만)
    Episode findFirstByAniDetailIdAndEpisodeNumberGreaterThanAndIsReleasedTrueOrderByEpisodeNumberAsc(Long aniDetailId, Integer episodeNumber);
}
