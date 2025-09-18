package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.CreateReviewCommentsRequestDto;
import com.ottproject.ottbackend.dto.ReviewCommentsResponseDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.dto.UpdateReviewCommentsRequestDto;
import com.ottproject.ottbackend.enums.CommentStatus;
import com.ottproject.ottbackend.service.ReviewCommentsService;
import com.ottproject.ottbackend.util.SecurityUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.util.List;

/**
 * ReviewCommentsController
 *
 * 큰 흐름
 * - 리뷰 하위 댓글 컬렉션에 대한 생성/목록/상태변경과 개별 댓글의 수정/삭제/신고/좋아요/대댓글을 제공한다.
 *
 * 엔드포인트 개요
 * - POST /api/reviews/{reviewId}/comments: 댓글 생성
 * - GET /api/reviews/{reviewId}/comments: 댓글 목록(페이지)
 * - PATCH /api/reviews/{reviewId}/comments/{commentId}/status: 댓글 상태 변경
 * - DELETE /api/reviews/{reviewId}/comments: 리뷰의 모든 댓글 삭제(관리용)
 * - PUT /api/comments/{commentId}: 댓글 수정
 * - DELETE /api/comments/{commentId}: 댓글 소프트 삭제
 * - POST /api/comments/{commentId}/report: 댓글 신고
 * - POST /api/comments/{commentId}/like: 댓글 좋아요 토글
 * - GET /api/comments/{commentId}/replies: 대댓글 목록
 * - POST /api/comments/{commentId}/replies: 대댓글 생성
 */
@RequiredArgsConstructor // final 필드 기반 생성자 자동 생성
@RestController
@RequestMapping("/api/reviews/{reviewId}/comments") // 리뷰 상세 하위: 댓글 컬렉션 경로
public class ReviewCommentsController { // 댓글 목록/대댓글/작성/상태변경/일괄삭제 담당 컨트롤러

    private final ReviewCommentsService reviewCommentsService; // 댓글 서비스 의존성
    private final SecurityUtil securityUtil; // 세션 → 사용자 ID 해석 유틸


    @Operation(summary = "댓글 생성", description = "리뷰 하위에 최상위 댓글을 생성합니다.")
    @ApiResponse(responseCode = "200", description = "생성 성공: 댓글 ID 반환")
    @PostMapping // POST /api/reviews{reviewId}/comments
    public ResponseEntity<Long> create( // 최상위 댓글 생성
                                        @Parameter(description = "리뷰 ID") @PathVariable Long reviewId, // 경로변수: 리뷰 ID
                                        @Valid @RequestBody CreateReviewCommentsRequestDto dto, // 요청 바디(JSON)로 내용 수신
                                        HttpSession session // 세션에서 사용자 확인
    ) {
		Long userId = securityUtil.requireCurrentUserId(session); // 로그인 필수
		Long id  = reviewCommentsService.create(userId, reviewId, null, dto.getContent()); // parentId = null(최상위)
        return ResponseEntity.ok(id); // 200 + 생성 ID
    }

    @Operation(summary = "댓글 목록", description = "리뷰 하위 댓글 목록을 페이지네이션으로 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping // Get /api/reviews/{reviewId}/comments
    public ResponseEntity<PagedResponse<ReviewCommentsResponseDto>> listByReview( // 최상위 댓글 목록(페이지네이션)
                                                                                  @Parameter(description = "리뷰 ID") @PathVariable Long reviewId, // 경로변수: 리뷰 ID
                                                                                  @RequestParam(defaultValue = "0") int page, // 페이지 번호(0-base)
                                                                                  @RequestParam(defaultValue = "10") int size, // 페이지 크기
                                                                                  @RequestParam(defaultValue = "latest") String sort, // latest|best
                                                                                  HttpSession session // 세션(선택 로그인)
    ) {
        Long currentUserId = securityUtil.getCurrentUserIdOrNull(session); // 로그인 시 사용자 ID, 아니면 null
        return ResponseEntity.ok(reviewCommentsService.listByReview(reviewId, currentUserId, page, size, sort)); // 서비스 위임
    }

    @Operation(summary = "댓글 상태 변경", description = "DELETED/REPORTED 등 상태를 변경합니다.")
    @ApiResponse(responseCode = "204", description = "변경 완료")
    @PatchMapping("/{commentId}/status") // PATCH /api/reviews/{reviewId}/comments/{commentId}/status
    public ResponseEntity<Void> updateStatus( // 댓글 상태 변경(소프트 삭제/복구/신고 등)
            @Parameter(description = "리뷰 ID") @PathVariable Long reviewId, // 경로변수: 리뷰 ID(경로 일관성 유지)
            @Parameter(description = "댓글 ID") @PathVariable Long commentId, // 경로변수: 댓글 ID
            @RequestParam CommentStatus status // 쿼리파라미터: 상태 값

    ) {
        reviewCommentsService.updateStatus(commentId, status); // 상태 갱신
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @Operation(summary = "리뷰 댓글 일괄 삭제", description = "특정 리뷰의 모든 댓글을 하드 삭제합니다.(관리용)")
    @ApiResponse(responseCode = "204", description = "삭제 완료")
    @DeleteMapping // DELETE /api/reviews/{reviewId}/comments
    public ResponseEntity<Void> deleteAllByReview( // 특정 리뷰의 댓글 일괄 삭제(관리용)
            @Parameter(description = "리뷰 ID") @PathVariable Long reviewId // 경로변수: 리뷰 ID
    ) {
        reviewCommentsService.deleteHardByReview(reviewId); // 일괄 하드 삭제
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @Operation(summary = "댓글 수정", description = "본인 댓글 내용을 수정합니다.")
    @ApiResponse(responseCode = "204", description = "수정 완료")
    @PutMapping("/{commentId}") // 클래스 레벨 경로 기준
    public ResponseEntity<Void> update( // 본인 댓글 수정
                                        @Parameter(description = "리뷰 ID") @PathVariable Long reviewId, // 클래스 레벨 경로 변수 매핑
                                        @Parameter(description = "댓글 ID") @PathVariable Long commentId, // 경로변수: 댓글 ID
                                        @Valid @RequestBody UpdateReviewCommentsRequestDto dto, // 요청바디: 수정 내용
                                        HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = securityUtil.requireCurrentUserId(session); // 로그인 필수
        reviewCommentsService.updateContent(commentId, userId, dto.getContent()); // 서비스 위임
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @Operation(summary = "댓글 삭제", description = "본인 댓글을 소프트 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "삭제 완료")
    @DeleteMapping("/{commentId}") // 클래스 레벨 경로 기준
    public ResponseEntity<Void> delete( // 본인 댓글 소프트 삭제
            @Parameter(description = "리뷰 ID") @PathVariable Long reviewId, // 클래스 레벨 경로 변수 매핑
            @Parameter(description = "댓글 ID") @PathVariable Long commentId, // 경로변수: 댓글 ID
            HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = securityUtil.requireCurrentUserId(session); // 로그인 필수
        reviewCommentsService.deleteSoft(commentId, userId); // 상태 DELETED 전환
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @Operation(summary = "댓글 신고", description = "본인 외 댓글을 신고합니다.")
    @ApiResponse(responseCode = "204", description = "신고 접수")
    @PostMapping("/{commentId}/report") // 클래스 레벨 경로 기준
    public ResponseEntity<Void> report( // 댓글 신고
            @Parameter(description = "리뷰 ID") @PathVariable Long reviewId, // 클래스 레벨 경로 변수 매핑
            @Parameter(description = "댓글 ID") @PathVariable Long commentId, // 경로변수: 댓글 ID
            HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = securityUtil.requireCurrentUserId(session); // 로그인 필수
        reviewCommentsService.report(commentId, userId); // 상태 REPORTED 전환
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @Operation(summary = "댓글 좋아요 토글", description = "좋아요 on/off를 토글합니다.")
    @ApiResponse(responseCode = "200", description = "토글 결과 반환")
    @PostMapping("/{commentId}/like") // 클래스 레벨 경로 기준
    public ResponseEntity<Boolean> toggleLike( // 좋아요 토글(true=on, false=off)
            @Parameter(description = "리뷰 ID") @PathVariable Long reviewId, // 클래스 레벨 경로 변수 매핑
            @Parameter(description = "댓글 ID") @PathVariable Long commentId, // 경로변수: 댓글 Id
            HttpSession session // 세션에서 사용자 확인
    ) {
        try {
            Long userId = securityUtil.requireCurrentUserId(session); // 로그인 필수
            System.out.println("🔧 [BACKEND] ReviewComment 좋아요 토글 요청 - reviewId: " + reviewId + ", commentId: " + commentId + ", userId: " + userId);
            boolean result = reviewCommentsService.toggleLike(commentId, userId);
            System.out.println("🔧 [BACKEND] ReviewComment 좋아요 토글 결과: " + result);
            return ResponseEntity.ok(result); // 200 OK + 토글 결과
        } catch (IllegalArgumentException e) {
            System.out.println("🔧 [BACKEND] ReviewComment 좋아요 토글 실패 - IllegalArgumentException: " + e.getMessage());
            // 댓글이 존재하지 않거나 사용자를 찾을 수 없는 경우
            return ResponseEntity.notFound().build(); // 404 Not Found
        } catch (Exception e) {
            System.out.println("🔧 [BACKEND] ReviewComment 좋아요 토글 실패 - Exception: " + e.getMessage());
            // 기타 예외
            return ResponseEntity.status(500).build(); // 500 Internal Server Error
        }
    }

    @Operation(summary = "대댓글 목록", description = "특정 댓글의 대댓글을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{commentId}/replies") // 클래스 레벨 경로 기준
    public ResponseEntity<List<ReviewCommentsResponseDto>> replies( // 대댓글 목록(플랫)
                                                                    @Parameter(description = "리뷰 ID") @PathVariable Long reviewId, // 클래스 레벨 경로 변수 매핑
                                                                    @Parameter(description = "댓글 ID") @PathVariable Long commentId, // 경로변수: 부모댓글 ID
                                                                    HttpSession session // 세션(선택 로그인)
    ) {
        Long currentUserId = securityUtil.getCurrentUserIdOrNull(session); // 로그인 시 ID, 아니면 null
        return ResponseEntity.ok(reviewCommentsService.listReplies(commentId, currentUserId)); // 200 OK + 리스트
    }

	@Operation(summary = "대댓글 생성", description = "특정 댓글의 자식 댓글을 생성합니다.")
	@ApiResponse(responseCode = "200", description = "생성 성공: 댓글 ID 반환")
	@PostMapping("/{commentId}/replies") // 클래스 레벨 경로 기준
    public ResponseEntity<Long> createReply( // 대댓글 생성
                                             @Parameter(description = "리뷰 ID") @PathVariable Long reviewId, // 클래스 레벨 경로 변수 매핑
                                             @Parameter(description = "댓글 ID") @PathVariable Long commentId, // 경로변수: 부모 댓글 ID
                                             @Valid @RequestBody CreateReviewCommentsRequestDto dto, // 요청 바디(JSON)
                                             HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = securityUtil.requireCurrentUserId(session); // 로그인 필수
        return ResponseEntity.ok(reviewCommentsService.createReply(userId, commentId, dto.getContent())); // 200 OK + 생성 ID
    }
}
