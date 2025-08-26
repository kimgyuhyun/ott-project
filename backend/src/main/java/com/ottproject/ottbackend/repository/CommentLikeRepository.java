package com.ottproject.ottbackend.repository;
import com.ottproject.ottbackend.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CommentLikeRepository
 *
 * 큰 흐름
 * - 댓글 좋아요 CUD를 담당하는 JPA 리포지토리.
 *
 * 메서드 개요
 * - deleteByUserIdAndCommentId: 사용자 토글 off 용 삭제
 */
@Repository // 빈 등록
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> { // CUD 전용
    int deleteByUser_IdAndComment_Id(Long userId, Long commentId); // 토글 off 용 삭제(CUD)
}