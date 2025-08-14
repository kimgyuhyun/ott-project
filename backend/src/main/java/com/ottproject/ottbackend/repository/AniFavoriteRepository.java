package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.AniFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository // 스프링 빈 등록
public interface AniFavoriteRepository extends JpaRepository<AniFavorite, Long> { // 리포지토리 인터페이스
    Optional<AniFavorite> findByUserIdAndAniListId(Long userId, Long aniId); // 특정 유저-작품 찜 조회
    boolean existsByUserIdAndAniListId(Long userId, Long aniId); // 찜 여부 확인
    void deleteByUserIdAndAniListId(Long userId, Long aniId); // 찜 삭제(멱등)
    long countByAniListId(Long aniId); // 작품별 찜 수(옵션)
}