package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.ReviewReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 리뷰 신고 기록 리포지토리
 */
@Repository
public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {
    boolean existsByReview_IdAndUser_Id(Long reviewId, Long userId); // 사용자당 1회 신고 여부
    long countByReview_Id(Long reviewId); // 해당 리뷰 누적 신고 수
}
