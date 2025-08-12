package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.CommentResponseDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.entity.Comment;
import com.ottproject.ottbackend.entity.Review;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.CommentStatus;
import com.ottproject.ottbackend.mybatis.ReviewCommentQueryMapper;
import com.ottproject.ottbackend.repository.CommentRepository;
import com.ottproject.ottbackend.repository.ReviewRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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

    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public PagedResponse<CommentResponseDto> listByReview(Long reviewId, Long currentUserId, int page, int size) {
        int limit = size; // LIMIT 계산
        int offset = Math.max(page, 0) * size; // OFFSET 계산(0 미만 보호)
        List<CommentResponseDto> items = commentQueryMapper
                .findCommentsByReviewId(reviewId, currentUserId, limit, offset);
        long total = commentQueryMapper.countCommentsByReviewId(reviewId); // 총 개수 조회
        return new PagedResponse<>(items, total, page, size); // 표준 페이지 응답
    }

    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public List<CommentResponseDto> listReplies(Long parentId, Long currentUserId) {
        return commentQueryMapper.findRepliesByParentId(parentId, currentUserId); // 대댓글 목록
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

    public void updateStatus(Long commentId, CommentStatus status) {
        int updated = commentRepository.updateStatus(commentId, status); // 상태 갱신(DML)
        if (updated == 0) throw new IllegalArgumentException("comment not found: " + commentId); // 없으면 예외
    }

    public void deleteHardByReview(Long reviewId) {
        commentRepository.deleteByReviewId(reviewId); // 특정 리뷰의 댓글 하드 삭제
    }
}
