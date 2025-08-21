package com.ottproject.ottbackend.repository;
import com.ottproject.ottbackend.entity.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * ReviewLikeRepository
 *
 * 큰 흐름
 * - 리뷰 좋아요 CUD를 담당하는 JPA 리포지토리.
 *
 * 메서드 개요
 * - deleteByUserIdAndReviewId: 사용자 토글 off 용 삭제
 */
@Repository // 빈 등록
public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> { // CUD 전용
    int deleteByUserIdAndReviewId(Long userId, Long reviewId); // 토글 off 용 삭제(CUD)
}