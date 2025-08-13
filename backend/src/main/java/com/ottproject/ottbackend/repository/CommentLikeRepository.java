package com.ottproject.ottbackend.repository;
import com.ottproject.ottbackend.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository // 빈 등록
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> { // CUD 전용
    int deleteByUserIdAndCommentId(Long userId, Long commentId); // 토글 off 용 삭제(CUD)
}