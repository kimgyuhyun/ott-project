package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.ReviewCommentsResponseDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.entity.Comment;
import com.ottproject.ottbackend.entity.CommentLike;
import com.ottproject.ottbackend.entity.CommentReport;
import com.ottproject.ottbackend.entity.Review;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.CommentStatus;
import com.ottproject.ottbackend.mybatis.CommunityReviewCommentQueryMapper;
import com.ottproject.ottbackend.repository.CommentLikeRepository;
import com.ottproject.ottbackend.repository.CommentReportRepository;
import com.ottproject.ottbackend.repository.CommentRepository;
import com.ottproject.ottbackend.repository.ReviewRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import com.ottproject.ottbackend.service.NotificationTriggerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
    private final CommentReportRepository commentReportRepository; // 신고 기록 CUD
    private final NotificationTriggerService notificationTriggerService; // 알림 트리거 서비스

    private static final int REPORT_HIDE_THRESHOLD = 5; // 서로 다른 사용자 신고가 이 수 이상이면 숨김(REPORTED)

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

        Comment comment = (parent == null) // 부모 유무로 댓글/대댓글 분기(불필요한 중복 생성 제거)
                ? Comment.createComment(user, review, content)
                : Comment.createReply(user, review, parent, content);

        Comment savedComment = commentRepository.save(comment); // 저장 후 ID 반환
        
        // 모든 댓글에 대해 알림 생성 (자신의 리뷰가 아닌 경우만)
        try {
            notificationTriggerService.triggerReviewCommentNotification(savedComment);
        } catch (Exception e) {
            log.warn("comment notification failed (ignored)", e);
        }
        
        return savedComment.getId();
    }

    public void updateContent(Long commentId, Long userId, String content) { // 본인 댓글 수정
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("comment not found: " + commentId));
        if (!comment.getUser().getId().equals(userId)) throw new SecurityException("forbidden");
        if (comment.getStatus() != CommentStatus.ACTIVE) throw new IllegalStateException("수정할 수 없는 댓글입니다."); // 삭제/신고된 댓글 수정 불가
        comment.updateContent(content); // 내용 갱신(길이/공백 검증 포함)
        commentRepository.save(comment); // 저장
    }

    public void deleteSoft(Long commentId, Long userId) { // 본인 댓글 소프트 삭제(상태 전환)
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("comment not found: " + commentId));
        if (!comment.getUser().getId().equals(userId)) throw new SecurityException("forbidden"); // 소유자 검증
        comment.setStatus(CommentStatus.DELETED); // 상태 전환
        commentRepository.save(comment); // 저장
    }

    public void report(Long commentId, Long userId) { // 댓글 신고(사용자당 1회, 임계치 초과 시에만 숨김)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("comment not found: " + commentId));

        if (commentReportRepository.existsByComment_IdAndUser_Id(commentId, userId)) {
            return; // 이미 신고한 사용자는 중복 신고 무시(단독 반복 신고로 숨김 방지)
        }
        commentReportRepository.save(CommentReport.create(comment, user)); // 신고 기록 저장

        long reports = commentReportRepository.countByComment_Id(commentId); // 서로 다른 사용자 누적 신고 수
        if (reports >= REPORT_HIDE_THRESHOLD && comment.getStatus() == CommentStatus.ACTIVE) {
            comment.setStatus(CommentStatus.REPORTED); // 임계치 초과 시에만 숨김
            commentRepository.save(comment);
        }
    }

    public boolean toggleLike(Long commentId, Long userId) { // 좋아요 토글(delete-first 전략)
        
        try {
            int deleted = commentLikeRepository.deleteByUser_IdAndComment_Id(userId, commentId); // 먼저 off 시도
            if (deleted > 0) {
                return false; // off
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
            
            Comment comment = commentRepository.findById(commentId)
                    .orElseThrow(() -> new IllegalArgumentException("comment not found: " + commentId));
            
            try {
                CommentLike like = CommentLike.createLike(user, comment);
                CommentLike savedLike = commentLikeRepository.save(like); // on 시도
                
                // 좋아요 알림 생성 (실패해도 좋아요는 정상 처리)
                try {
                    notificationTriggerService.triggerCommentLikeNotification(savedLike);
                } catch (Exception e) {
            log.warn("comment notification failed (ignored)", e);
        }
                
                return true; // on
            } catch (DataIntegrityViolationException e) { // 경합 대비: 이미 on 이었다면 off 로 수렴
                commentLikeRepository.deleteByUser_IdAndComment_Id(userId, commentId);
                return false; // off
            }
        } catch (Exception e) {
            log.error("toggleLike failed - commentId: {}", commentId, e);
            throw e;
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
