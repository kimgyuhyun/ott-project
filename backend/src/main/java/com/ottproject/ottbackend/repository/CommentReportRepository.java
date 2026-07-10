package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.CommentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 리뷰 댓글 신고 기록 리포지토리
 */
@Repository
public interface CommentReportRepository extends JpaRepository<CommentReport, Long> {
    boolean existsByComment_IdAndUser_Id(Long commentId, Long userId); // 사용자당 1회 신고 여부
    long countByComment_Id(Long commentId); // 해당 댓글 누적 신고 수
}
