package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.EpisodeCommentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 에피소드 댓글 신고 기록 리포지토리
 */
@Repository
public interface EpisodeCommentReportRepository extends JpaRepository<EpisodeCommentReport, Long> {
    boolean existsByEpisodeComment_IdAndUser_Id(Long episodeCommentId, Long userId); // 사용자당 1회 신고 여부
    long countByEpisodeComment_Id(Long episodeCommentId); // 해당 댓글 누적 신고 수
}
