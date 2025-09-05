package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.ReviewCommentsResponseDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.entity.Comment;
import com.ottproject.ottbackend.entity.CommentLike;
import com.ottproject.ottbackend.entity.Review;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.CommentStatus;
import com.ottproject.ottbackend.mybatis.CommunityReviewCommentQueryMapper;
import com.ottproject.ottbackend.repository.CommentLikeRepository;
import com.ottproject.ottbackend.repository.CommentRepository;
import com.ottproject.ottbackend.repository.ReviewRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ReviewCommentsService
 *
 * 큰 흐름
 * - 댓글/대댓글 목록 읽기(MyBatis)와 생성/수정/삭제/신고/좋아요 CUD(JPA)를 담당한다.
 *
 * 메서드 개요
 * - listByReview/listReplies: 댓글/대댓글 목록
 * - create/createReply/updateContent/deleteSoft/report: 댓글 생성/대댓글 생성/수정/소프트 삭제/신고
 * - toggleLike: 좋아요 토글(멱등 수렴)
 * - updateStatus/deleteHardByReview: 상태 갱신/리뷰 기준 하드 삭제
 */
@RequiredArgsConstructor // final 필드 주입용 생성자 자동 생성
@Service
@Transactional // 쓰기 메서드 트랜잭션 관리
public class ReviewCommentsService {

    // MyBatis 조회 매퍼(목록/대댓글/카운트)
    private final CommunityReviewCommentQueryMapper commentQueryMapper; // 읽기 전용(댓글 목록/대댓글 카운트)
    // JPA 저장/수정/삭제
    private final CommentRepository commentRepository; // 댓글 CUD
    private final ReviewRepository reviewRepository; // 부모 리뷰 검증/연관
    private final UserRepository userRepository; // 작성자 검증/연관
    private final CommentLikeRepository commentLikeRepository; // 좋아요 CUD

    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public PagedResponse<ReviewCommentsResponseDto> listByReview(Long reviewId, Long currentUserId, int page, int size) {
        int limit = size; // LIMIT 계산
        int offset = Math.max(page, 0) * size; // OFFSET 계산(0 미만 보호)
        List<ReviewCommentsResponseDto> items = commentQueryMapper
                .findCommentsByReviewId(reviewId, currentUserId, "latest", limit, offset);
        long total = commentQueryMapper.countCommentsByReviewId(reviewId); // 총 개수 조회
        return new PagedResponse<>(items, total, page, size); // 표준 페이지 응답
    }

    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public List<ReviewCommentsResponseDto> listReplies(Long parentId, Long currentUserId) {
        return commentQueryMapper.findRepliesByParentId(parentId, currentUserId); // 대댓글 목록
    }

    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public PagedResponse<ReviewCommentsResponseDto> listByReview(Long reviewId, Long currentUserId, int page, int size, String sort) { // [NEW]
        int limit = size; // LIMIT 계산
        int offset = Math.max(page, 0) * size; // OFFSET 계산(0 미만 보호)
        List<ReviewCommentsResponseDto> items = commentQueryMapper
                .findCommentsByReviewId(reviewId, currentUserId, sort, limit, offset); // [NEW]
        long total = commentQueryMapper.countCommentsByReviewId(reviewId); // 총 개수 조회
        return new PagedResponse<>(items, total, page, size); // 표준 페이지 응답
    }

    public Long create(Long userId, Long reviewId, Long parentId, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("review not found: " + reviewId));

        Comment parent = null;
        if (parentId != null) {
            parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("parent comment not found: " + parentId));
            // 선택 검증: 부모 댓글이 같은 리뷰에 속하는지
            if (parent.getReview() != null && !parent.getReview().getId().equals(reviewId)) {
                throw new IllegalArgumentException("parent/review mismatch");
            }
        }

        Comment comment = Comment.createComment( // 댓글 엔티티 생성
                user, // 연관: 작성자
                review, // 연관: 부모 리뷰
                content // 내용
        );
        if (parent != null) {
            comment = Comment.createReply(user, review, parent, content);
        }

        return commentRepository.save(comment).getId(); // 저장 후 ID 반환
    }

    public void updateContent(Long commentId, Long userId, String content) { // 본인 댓글 수정
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("comment not found: " + commentId));
        if (!comment.getUser().getId().equals(userId)) throw new SecurityException("forbidden");
        comment.setContent(content); // 내용 갱신
        commentRepository.save(comment); // 저장
    }

    public void deleteSoft(Long commentId, Long userId) { // 본인 댓글 소프트 삭제(상태 전환)
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("comment not found: " + commentId));
        if (!comment.getUser().getId().equals(userId)) throw new SecurityException("forbidden"); // 소유자 검증
        comment.setStatus(CommentStatus.DELETED); // 상태 전환
        commentRepository.save(comment); // 저장
    }

    public void report(Long commentId, Long userId) { // 댓글 신고(누구나 가능)
        // 필요 시 사용자 존재만 검증
        userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("comment not found: " + commentId));
        comment.setStatus(CommentStatus.REPORTED);
        commentRepository.save(comment);
    }

    public boolean toggleLike(Long commentId, Long userId) { // 좋아요 토글(delete-first 전략)
        int deleted = commentLikeRepository.deleteByUser_IdAndComment_Id(userId, commentId); // 먼저 off 시도
        if (deleted > 0) {
            return false; // off
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("comment not found: " + commentId));
        try {
            commentLikeRepository.save(CommentLike.createLike(user, comment)); // on 시도
            return true; // on
        } catch (DataIntegrityViolationException e) { // 경합 대비: 이미 on 이었다면 off 로 수렴
            commentLikeRepository.deleteByUser_IdAndComment_Id(userId, commentId);
            return false; // off
        }
    }

    public void updateStatus(Long commentId, CommentStatus status) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("comment not found: " + commentId));
        comment.setStatus(status);
        commentRepository.save(comment);
    }

    public Long createReply(Long userId, Long parentId, String content) { // 대댓글 생성(부모에서 리뷰 ID 유추)
        User user = userRepository.findById(userId) // 사용자 조회(필수)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId)); // 없으면 예외
        Comment parent = commentRepository.findById(parentId) // 부모 댓글 조회(필수)
                .orElseThrow(() -> new IllegalArgumentException("parent comment not found: " + parentId)); // 없으면 예외
        Review review = parent.getReview(); // 부모 댓글이 속한 리뷰 엔티티 추출

        Comment reply = Comment.createReply( // 댓글 엔티티 빌드
                user, // 작성자 연관
                review, // 부모 댓글의 리뷰로 설정
                parent, // 부모 댓글 연관
                content // 내용
        ); // 엔티티 생성 완료

        return commentRepository.save(reply).getId(); // 저장 후 생성 PK 반환
    }

    public void deleteHardByReview(Long reviewId) {
        commentRepository.deleteByReview_Id(reviewId); // 파생 삭제로 대체
    }
}
