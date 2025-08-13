package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.CommentResponseDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.enums.CommentStatus;
import com.ottproject.ottbackend.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor // final 필드 기반 생성자 자동 생성
@RestController
@RequestMapping("/api/reviews/{reviewId}/comments") // 리뷰 상세 하위: 댓글 컬렉션 경로
public class CommentController { // 댓글 목록/대댓글/작성/상태변경/일괄삭제 담당 컨트롤러

    private final CommentService commentService; // 댓글 서비스 의존성

    @GetMapping // gET /api/reviews/{reviewId}/comments
    public ResponseEntity<PagedResponse<CommentResponseDto>> listByReview( // 최상위 댓글 목록(페이지네이션)
            @PathVariable Long reviewId, // 경로변수: 리뷰 ID
            @RequestParam(required = false) Long currentUserId, // 선택: 현재 사용자 ID(좋아요 여부 계산용)
            @RequestParam(defaultValue = "0") int page, // 페이지 번호(0-base)
            @RequestParam(defaultValue = "10") int size, // 페이지 크기
            @RequestParam(defaultValue = "latest") String sort // latest|best
    ) {
        return ResponseEntity.ok( // 200 OK
                commentService.listByReview(reviewId, currentUserId, page, size, sort) // 서비스 위임
        );
    }

    @PatchMapping("/{commentId}/status") // PATCH /api/reviews/{reviewId}/comments/{commentId}/status
    public ResponseEntity<Void> updateStatus( // 댓글 상태 변경(소프트 삭제/복구/신고 등)
            @PathVariable Long reviewId, // 경로변수: 리부ㅠ ID(경로 일관성 유지)
            @PathVariable Long commentId, // 경로변수: 댓글 ID
            @RequestParam CommentStatus status // 쿼리파라미터: 상태 값

    ) {
        commentService.updateStatus(commentId, status); // 상태 갱신
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @DeleteMapping // DELETE /api/reviews/{reviewId}/comments
    public ResponseEntity<Void> deleteAllByReview( // 특정 리뷰의 댓글 일괄 삭제(관리용)
            @PathVariable Long reviewId // 경로변수: 리뷰 ID
    ) {
        commentService.deleteHardByReview(reviewId); // 일괄 하드 삭제
        return ResponseEntity.noContent().build(); // 204 No Content
    }
}
