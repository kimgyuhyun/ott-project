package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.CreateCommentRequestDto;
import com.ottproject.ottbackend.dto.CommentResponseDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.dto.UpdateCommentRequestDto;
import com.ottproject.ottbackend.enums.CommentStatus;
import com.ottproject.ottbackend.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor // final 필드 기반 생성자 자동 생성
@RestController
@RequestMapping("/api/reviews/{reviewId}/comments") // 리뷰 상세 하위: 댓글 컬렉션 경로
public class CommentController { // 댓글 목록/대댓글/작성/상태변경/일괄삭제 담당 컨트롤러

    private final CommentService commentService; // 댓글 서비스 의존성


    @PostMapping // POST /api/reviews{reviewId}/comments
    public ResponseEntity<Long> create( // 최상위 댓글 생성
            @PathVariable Long reviewId, // 경로변수: 리뷰 ID
			@RequestParam Long userId, // 작성자 ID(인증 연동 전 임시
			@Valid @RequestBody CreateCommentRequestDto dto // 요청 바디(JSON)로 내용 수신
    ) {
		Long id  = commentService.create(userId, reviewId, null, dto.getContent()); // parentId = null(최상위)
        return ResponseEntity.ok(id); // 200 + 생성 ID
    }

    @GetMapping // Get /api/reviews/{reviewId}/comments
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
            @PathVariable Long reviewId, // 경로변수: 리뷰 ID(경로 일관성 유지)
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

    @PutMapping("/api/comments/{commentId}") // 절대 경로: PUT
    public ResponseEntity<Void> update( // 본인 댓글 수정
            @PathVariable Long commentId, // 경로변수: 댓글 ID
            @RequestParam Long userId, // 작성자 ID(인증 연동 전 임시)
            @Valid @RequestBody UpdateCommentRequestDto dto // 요청바디: 수정 내용
    ) {
        commentService.updateContent(commentId, userId, dto.getContent()); // 서비스 위임
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @DeleteMapping("/api/comments/{commentId}") // 절대 경로: DELETE
    public ResponseEntity<Void> delete( // 본인 댓글 소프트 삭제
            @PathVariable Long commentId, // 경로변수: 댓글 ID
            @RequestParam Long userId // 작성자 ID
    ) {
        commentService.deleteSoft(commentId, userId); // 상태 DELETED 전환
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @PostMapping("/api/comments/{commentId}/report") // 절대 경로: POST
    public ResponseEntity<Void> report( // 댓글 신고
            @PathVariable Long commentId, // 경로변수: 댓글 ID
            @RequestParam Long userId // 신고자 ID
    ) {
        commentService.report(commentId, userId); // t상태 REPORTED 전환
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @PostMapping("/api/comments/{commentId}/like") // 절대 경로 POST
    public ResponseEntity<Boolean> toggleLike( // 좋아요 토글(true=on, false=off
            @PathVariable Long commentId, // 경로변수: 댓글 Id
            @RequestParam Long userId // 사용자 ID
    ) {
        return ResponseEntity.ok(commentService.toggleLike(commentId, userId)); // 200 OK + 토글 결과
    }

    @GetMapping("/api/comments/{commentId}/replies") // 절대 경로: GET
    public ResponseEntity<List<CommentResponseDto>> replies( // 대댓글 목록(플랫)
            @PathVariable Long commentId, // 경로변수: 부모댓글 ID
            @RequestParam(required = false) Long currentUserId // 선택 현재 사용자 ID(좋아요 여부 계산용)
    ) {
        return ResponseEntity.ok(commentService.listReplies(commentId, currentUserId)); // 200 OK + 리스트
    }

	@PostMapping("/api/comments/{commentId}/replies") // 절대 경로: POST
	public ResponseEntity<Long> createReply( // 대댓글 생성
			@PathVariable Long commentId, // 경로변수: 부모 댓글 ID
			@RequestParam Long userId, // 작성자 ID(인증 연동 전 임시)
			@Valid @RequestBody CreateCommentRequestDto dto // 요청 바디(JSON)
	) {
		return ResponseEntity.ok(commentService.createReply(userId, commentId, dto.getContent())); // 200 OK + 생성 ID
	}
}
