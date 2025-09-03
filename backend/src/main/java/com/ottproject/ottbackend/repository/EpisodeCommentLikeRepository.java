package com.ottproject.ottbackend.repository;
import com.ottproject.ottbackend.entity.EpisodeCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * EpisodeCommentLikeRepository
 *
 * 큰 흐름
 * - 에피소드 댓글 좋아요 CUD를 담당하는 JPA 리포지토리.
 *
 * 메서드 개요
 * - deleteByUserIdAndEpisodeCommentId: 사용자 토글 off 용 삭제
 */
@Repository // 빈 등록
public interface EpisodeCommentLikeRepository extends JpaRepository<EpisodeCommentLike, Long> { // CUD 전용
    int deleteByUser_IdAndEpisodeComment_Id(Long userId, Long episodeCommentId); // 토글 off 용 삭제(CUD)
}
