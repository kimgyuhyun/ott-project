package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.AnimeDetail;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AnimeDetail CUD 전용 리포지토리
 * - 상세 정보의 생성/수정/삭제 담당
 * - 조회(R)는 MyBatis 매퍼에서 DTO 로 반환
 */
@Repository
public interface AnimeDetailRepository extends JpaRepository<AnimeDetail, Long> { // CUD 전용

    @Lock(LockModeType.PESSIMISTIC_WRITE) // 수정/삭제 직전 충돌 방지
    @Query("select d from AnimeDetail d where d.id = :id") // 잠금용 단건 조회
    Optional<AnimeDetail> findByIdForUpdate(@Param("id") Long id); // :id 바인딩

    @Modifying(clearAutomatically = true, flushAutomatically = true) // DML + 동기화
    @Query("delete from AnimeDetail d where d.animeList.id = :aniListId") // 부모 기준 일괄 삭제
    int deleteByAniListId(@Param("aniListId") Long aniListId); // 삭제 행 수
}
