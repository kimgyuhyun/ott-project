package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Comment;
import com.ottproject.ottbackend.enums.CommentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository // 스프링 빈 등록
public interface CommentRepository extends JpaRepository<Comment, Long> { // 기본 CUD 제공


    @Lock(LockModeType.PESSIMISTIC_WRITE) // 쓰기 락
    @Query("select c from Comment c where c.id = :id") // JPQL 단건 조회
    // pk 가 :id 인 댓글 엔티티 c 한 건을 조회
    Optional<Comment> findByIdForUpdate(@Param("id") Long id); // :id 바인딩

    @Modifying(clearAutomatically = true, flushAutomatically = true) // DML + 영속성 컨텍스트 자동 동기화 옵션 활성화
    @Query("update Comment c set c.status = :status where c.id = :id") // 상태 변경(소프트 삭제/복구/신고 등)
    // pk 가 :id 인 댓글의 status 컬럼을 :status 값으로 갱신
    int updateStatus(@Param("id") Long id, @Param("status")CommentStatus status); // 변경 행 수

    // 필요 시: 리뷰 삭제 시 댓글 일괄 하드 삭제(관리/정리용)
    @Modifying(clearAutomatically = true, flushAutomatically = true) // DML + 컨텍스트 동기화
    @Query("delete from Comment c where c.review.id = :reviewId") // 부모 리뷰 FK 기준 일괄 삭제
    // 특정 리뷰에 달린 모든 댓글을 하드 삭제
    int deleteByReviewId(@Param("reviewId") Long reviewId); // 삭제 행 수 반환
}
