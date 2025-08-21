package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Review;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import org.springframework.lang.NonNull;

import java.util.Optional;

/**
 * ReviewRepository
 *
 * 큰 흐름
 * - 리뷰 CUD를 제공하는 JPA 리포지토리.
 * - 대량 삭제는 파생 메서드(deleteByAnime_Id)로 처리한다.
 *
 * 메서드 개요
 * - findById: 비관적 락으로 단건 조회
 * - deleteByAnime_Id: 작품 기준 리뷰 일괄 삭제
 */
@Repository // 빈 등록
public interface ReviewRepository extends JpaRepository<Review, Long> { // 기본 CUD 제공

    @Lock(LockModeType.PESSIMISTIC_WRITE) // 쓰기 락(동시 수정 방지)
    @NonNull Optional<Review> findById(@NonNull Long id); // 파생 메서드 + @Lock로 대체

    // 파생 삭제: 부모 애니 기준 일괄 삭제
    int deleteByAnime_Id(Long aniId); // deleteBy + 경로식으로 대체
}
