package com.ottproject.ottbackend.mybatis;

import com.ottproject.ottbackend.dto.CommentResponseDto;
import com.ottproject.ottbackend.dto.ReviewResponseDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 리뷰/댓글 읽기 전용(Mybatis) 매퍼
 */
@Mapper
public interface ReviewCommentQueryMapper {

    // 리뷰 목록: 특정 애니(aniId) 기준
    List<ReviewResponseDto> findReviewsByAniId(
            @Param("aniId") Long aniId, // 대상 애니 ID
            @Param("currentUserId") Long currentUserId, // 현재 사용자 ID(비로그인 null)
            @Param("sort") String sort, // 정렬키: latest/likes/rating
            @Param("limit") int limit, // 페이지 크기
            @Param("offset") int offset // 오프셋
    );

    // 리뷰 목록 총 개수(페이지네이션용)
    long countReviewsByAniId(@Param("aniId") Long aniId);

    // 리뷰 단건(상세) 조회
    ReviewResponseDto findReviewById(
            @Param("reviewId") Long reviewId, // 리뷰 ID
            @Param("currentUserId") Long currentUserId // 현재 사용자 ID
    );

    // 댓글 목록: 특정 리뷰(reviewId) 기준(최상위 댓글만)
    List<CommentResponseDto> findCommentsByReviewId(
            @Param("reviewId") Long reviewId, // 대상 리뷰 ID
            @Param("currentUserId") Long currentUserId, // 현재 사용자 ID
            @Param("limit") int limit, // 페이지 크기
            @Param("offset") int offset // 오프셋
    );

    // 댓글 총 개수
    long countCommentsByReviewId(@Param("reviewId") Long reviewId);

    // 대댓글 목록: 특정 부모(parentId) 기준
    List<CommentResponseDto> findRepliesByParentId(
            @Param("parentId") Long parentId, // 부모 댓글 ID
            @Param("currentUserId") Long currentUserId // 현재 사용자 ID
    );
}


