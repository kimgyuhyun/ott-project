package com.ottproject.ottbackend.repository;
import com.ottproject.ottbackend.entity.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository // 빈 등록
public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> { // CUD 전용
    int deleteByUserIdAndReviewId(Long userId, Long reviewId); // 토글 off 용 삭제(CUD)
}