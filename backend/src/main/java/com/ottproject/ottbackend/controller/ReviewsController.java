package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.CreateReviewRequestDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.dto.ReviewResponseDto;
import com.ottproject.ottbackend.dto.UpdateReviewRequestDto;
import com.ottproject.ottbackend.service.ReviewsService;
import com.ottproject.ottbackend.util.SecurityUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * ReviewsController
 *
 * 큰 흐름
 * - 작품 리뷰에 대한 목록/작성/수정/삭제/신고/좋아요와 관리용 일괄 삭제를 제공한다.
 *
 * 엔드포인트 개요
 * - GET /api/anime/{aniId}/reviews: 리뷰 목록(페이지)
 * - POST /api/anime/{aniId}/reviews: 리뷰 작성
 * - DELETE /api/anime/{aniId}/reviews: 작품 리뷰 일괄 삭제(관리용)
 * - PUT /api/reviews/{reviewId}: 리뷰 수정
 * - DELETE /api/reviews/{reviewId}: 리뷰 소프트 삭제
 * - POST /api/reviews/{reviewId}/report: 리뷰 신고
 * - POST /api/reviews/{reviewId}/like: 리뷰 좋아요 토글
 */
@RequiredArgsConstructor // final 필드 기반 생성자 자동 생성(의존성 주입)
@RestController // REST 컨트롤러로 등록(메서드 반환값을 JSON 으로 직렬화)
@RequestMapping("/api/anime/{aniId}/reviews") // 상세 페이지 하위: 리뷰 컬렉션 경로
public class ReviewsController { // 리뷰 목록/작성/일괄삭제 담당 컨트롤러

    private final ReviewsService reviewsService; // 비즈니스 로직을 담당하는 서비스 주입
    private final SecurityUtil securityUtil; // 세션 → 사용자 ID 해석 유틸

    @Operation(summary = "리뷰 목록", description = "특정 작품의 리뷰 목록을 페이지네이션으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping // HTTP GET /api/anime/{aniId}/reviews
    public ResponseEntity<PagedResponse<ReviewResponseDto>> list( // 리뷰 목록(페이지네이션) 반환
            @Parameter(description = "애니 ID") @PathVariable Long aniId, // 경로변수: 애니 ID
            @RequestParam(defaultValue = "latest") String sort, // 정렬 기준(기본: 최신순)
            @RequestParam(defaultValue = "0") int page, // 페이지 번호(0-base)
            @RequestParam(defaultValue = "10") int size, // 페이지 크기
            HttpSession session // 세션(선택 로그인)
    ) {
        Long currentUserId = securityUtil.getCurrentUserIdOrNull(session); // 로그인 시 사용자 ID, 아니면 null
        return ResponseEntity.ok(reviewsService.list(aniId, currentUserId, sort, page, size)); // 200 OK + 본문
    }

    @Operation(summary = "리뷰 작성", description = "본문/평점을 입력해 리뷰를 작성합니다.")
    @ApiResponse(responseCode = "200", description = "생성 성공: 리뷰 ID 반환")
    @PostMapping // HTTP POST /api/anime/{aniId}/reviews
    public ResponseEntity<Long> create( // 생성된 리뷰의 PK(ID)를 반환
            @Parameter(description = "애니 ID") @PathVariable Long aniId, // 경로 변수: 애니 ID
            @Valid @RequestBody CreateReviewRequestDto dto,
            HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = securityUtil.requireCurrentUserId(session); // 로그인 필수
        Long id = reviewsService.create(userId, aniId, dto.getContent(), dto.getRating()); // 서비스 호출
        return ResponseEntity.ok(id); // 200 OK + 리뷰 ID
    }

    @Operation(summary = "리뷰 일괄 삭제", description = "특정 작품의 모든 리뷰를 하드 삭제합니다.(관리용)")
    @ApiResponse(responseCode = "204", description = "삭제 완료")
    @DeleteMapping // DELETE /api/anime/{aniId]/reviews
    public ResponseEntity<Void> deleteAllByAni( // 특정 애니의 리뷰 일괄 삭제(관리용)
            @Parameter(description = "애니 ID") @PathVariable Long aniId // 경로변수: 애니 ID
    ) {
        reviewsService.deleteHardByAniList(aniId); // 일괄 하드 삭제
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @Operation(summary = "리뷰 수정", description = "본인 리뷰의 내용/평점을 수정합니다.")
    @ApiResponse(responseCode = "204", description = "수정 완료")
    @PutMapping("/{reviewId}") // 클래스 레벨 경로 기준 상대 경로
    public ResponseEntity<Void> update( // 본인 리뷰 수정
            @Parameter(description = "애니 ID") @PathVariable Long aniId, // 클래스 레벨 경로 변수 매핑
            @Parameter(description = "리뷰 ID") @PathVariable Long reviewId, // 경로변수: 리뷰 ID
            @Valid @RequestBody UpdateReviewRequestDto dto, // 요청 바디: 수정 필드(content/rating)
            HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = securityUtil.requireCurrentUserId(session); // 로그인 필수
        reviewsService.update(reviewId, userId, dto.getContent(), dto.getRating()); // 서비스 위임
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @Operation(summary = "리뷰 삭제", description = "본인 리뷰를 소프트 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "삭제 완료")
    @DeleteMapping("/{reviewId}") // 클래스 레벨 경로 기준 상대 경로
    public ResponseEntity<Void> delete( // 본인 리뷰 소프트 삭제(상태 전환)
            @Parameter(description = "애니 ID") @PathVariable Long aniId, // 클래스 레벨 경로 변수 매핑
            @Parameter(description = "리뷰 ID") @PathVariable Long reviewId, // 경로변수: 리뷰 ID
            HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = securityUtil.requireCurrentUserId(session); // 로그인 필수
        reviewsService.deleteSoft(reviewId, userId); // 상태 DELETED 전환
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @Operation(summary = "리뷰 신고", description = "리뷰를 신고합니다.")
    @ApiResponse(responseCode = "204", description = "신고 접수")
    @PostMapping("/{reviewId}/report") // 클래스 레벨 경로 기준 상대 경로
    public ResponseEntity<Void> report( // 리뷰 신고
            @Parameter(description = "애니 ID") @PathVariable Long aniId, // 클래스 레벨 경로 변수 매핑
            @Parameter(description = "리뷰 ID") @PathVariable Long reviewId, // 경로변수: 리뷰 ID
            HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = securityUtil.requireCurrentUserId(session); // 로그인 필수
        reviewsService.report(reviewId, userId); // 상태 REPORTED 전환
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @Operation(summary = "리뷰 좋아요 토글", description = "좋아요 on/off를 토글합니다.")
    @ApiResponse(responseCode = "200", description = "토글 결과 반환")
    @PostMapping("/{reviewId}/like") // 클래스 레벨 경로 기준 상대 경로
    public ResponseEntity<Boolean> toggleLike( // 좋아요 토글(true=on, false=off)
            @Parameter(description = "애니 ID") @PathVariable Long aniId, // 클래스 레벨 경로 변수 매핑
            @Parameter(description = "리뷰 ID") @PathVariable Long reviewId, // 경로변수: 리뷰 ID
            HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = securityUtil.requireCurrentUserId(session); // 로그인 필수
        return ResponseEntity.ok(reviewsService.toggleLike(reviewId, userId)); // 200 OK + 토글 결과
    }
}
