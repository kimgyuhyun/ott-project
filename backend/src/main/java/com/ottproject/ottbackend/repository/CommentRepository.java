package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Comment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import org.springframework.lang.NonNull;

import java.util.Optional;

@Repository // 스프링 빈 등록
public interface CommentRepository extends JpaRepository<Comment, Long> { // 기본 CUD 제공


    @Lock(LockModeType.PESSIMISTIC_WRITE) // 쓰기 락
    @NonNull Optional<Comment> findById(@NonNull Long id); // 파생 메서드 + @Lock로 대체

    // 파생 삭제 메서드: 부모 리뷰 기준 일괄 삭제
    int deleteByReview_Id(Long reviewId); // deleteBy + 경로식 대체
}
