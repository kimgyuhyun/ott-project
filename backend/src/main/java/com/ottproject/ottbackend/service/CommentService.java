package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.CommentResponseDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.entity.Comment;
import com.ottproject.ottbackend.entity.CommentLike;
import com.ottproject.ottbackend.entity.Review;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.CommentStatus;
import com.ottproject.ottbackend.mybatis.ReviewCommentQueryMapper;
import com.ottproject.ottbackend.repository.CommentLikeRepository;
import com.ottproject.ottbackend.repository.CommentRepository;
import com.ottproject.ottbackend.repository.ReviewRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor // final 필드 주입용 생성자 자동 생성
@Service
@Transactional // 쓰기 메서드 트랜잭션 관리
public class CommentService {

    // MyBatis 조회 매퍼(목록/대댓글/카운트)
    private final ReviewCommentQueryMapper commentQueryMapper; // 읽기 전용(댓글 목록/대댓글 카운트)
    // JPA 저장/수정/삭제
    private final CommentRepository commentRepository; // 댓글 CUD
    private final ReviewRepository reviewRepository; // 부모 리뷰 검증/연관
    private final UserRepository userRepository; // 작성자 검증/연관
    private final CommentLikeRepository commentLikeRepository; // 좋아요 CUD

    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public PagedResponse<CommentResponseDto> listByReview(Long reviewId, Long currentUserId, int page, int size) {
        int limit = size; // LIMIT 계산
        int offset = Math.max(page, 0) * size; // OFFSET 계산(0 미만 보호)
        List<CommentResponseDto> items = commentQueryMapper
                .findCommentsByReviewId(reviewId, currentUserId, "latest", limit, offset);
        long total = commentQueryMapper.countCommentsByReviewId(reviewId); // 총 개수 조회
        return new PagedResponse<>(items, total, page, size); // 표준 페이지 응답
    }

    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public List<CommentResponseDto> listReplies(Long parentId, Long currentUserId) {
        return commentQueryMapper.findRepliesByParentId(parentId, currentUserId); // 대댓글 목록
    }

    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public PagedResponse<CommentResponseDto> listByReview(Long reviewId, Long currentUserId, int page, int size, String sort) { // [NEW]
        int limit = size; // LIMIT 계산
        int offset = Math.max(page, 0) * size; // OFFSET 계산(0 미만 보호)
        List<CommentResponseDto> items = commentQueryMapper
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

        Comment comment = Comment.builder() // 댓글 엔티티 생성
                .user(user) // 연관: 작성자
                .review(review) // 연관: 부모 리뷰
                .parent(parent) // 연관: 부모 댓글(옵션)
                .content(content) // 내용
                .status(CommentStatus.ACTIVE) // 기본 상태: ACTIVE
                .build();

        return commentRepository.save(comment).getId(); // 저장 후 ID 반환
    }

    public void updateContent(Long commentId, Long userId, String content) { // 본인 댓글 수정
        Comment comment = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new IllegalArgumentException("comment not found: " + commentId));
        if (!comment.getUser().getId().equals(userId)) throw new SecurityException("forbidden");
        comment.setContent(content); // 내용 갱신
        commentRepository.save(comment); // 저장
    }

    public void deleteSoft(Long commentId, Long userId) { // 본인 댓글 소프트 삭제(상태 전환)
        Comment comment = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new IllegalArgumentException("comment not found: " + commentId));
        if (!comment.getUser().getId().equals(userId)) throw new SecurityException("forbidden"); // 소유자 검증
        commentRepository.updateStatus(commentId, CommentStatus.DELETED); // 상태 전환
    }

    public void report(Long commentId, Long userId) { // 댓글 신고(누구나 가능)
        // 필요 시 사용자 존재만 검증
        userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        commentRepository.updateStatus(commentId, CommentStatus.REPORTED); // 상태 전환
    }

    public boolean toggleLike(Long commentId, Long userId) { // 좋아요 토글(C/D 만 사용, insert-first 전략)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("comment not found: " + commentId));
        try {
            commentLikeRepository.save(CommentLike.builder().user(user).comment(comment).build()); // on 시도
            return true; // on
        } catch (DataIntegrityViolationException e) {
            // 이미 on → off
            commentLikeRepository.deleteByUserIdAndCommentId(userId, commentId); // off
            return false; // off
        }
    }

    public void updateStatus(Long commentId, CommentStatus status) {
        int updated = commentRepository.updateStatus(commentId, status); // 상태 갱신(DML)
        if (updated == 0) throw new IllegalArgumentException("comment not found: " + commentId); // 없으면 예외
    }

    public void deleteHardByReview(Long reviewId) {
        commentRepository.deleteByReviewId(reviewId); // 특정 리뷰의 댓글 하드 삭제
    }
}
