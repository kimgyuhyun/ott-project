package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.CreateReviewRequestDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.dto.ReviewResponseDto;
import com.ottproject.ottbackend.dto.UpdateReviewRequestDto;
import com.ottproject.ottbackend.service.ReviewService;
import com.ottproject.ottbackend.util.AuthUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor // final 필드 기반 생성자 자동 생성(의존성 주입)
@RestController // REST 컨트롤러로 등록(메서드 반환값을 JSON 으로 직렬화)
@RequestMapping("/api/anime/{aniId}/reviews") // 상세 페이지 하위: 리뷰 컬렉션 경로
public class ReviewController { // 리뷰 목록/작성/일괄삭제 담당 컨트롤러

    private final ReviewService reviewService; // 비즈니스 로직을 담당하는 서비스 주입
    private final AuthUtil authUtil; // 세션 → 사용자 ID 해석 유틸

    @GetMapping // HTTP GET /api/anime/{aniId}/reviews
    public ResponseEntity<PagedResponse<ReviewResponseDto>> list( // 리뷰 목록(페이지네이션) 반환
            @PathVariable Long aniId, // 경로변수: 애니 ID
            @RequestParam(defaultValue = "latest") String sort, // 정렬 기준(기본: 최신순)
            @RequestParam(defaultValue = "0") int page, // 페이지 번호(0-base)
            @RequestParam(defaultValue = "10") int size, // 페이지 크기
            HttpSession session // 세션(선택 로그인)
    ) {
        Long currentUserId = authUtil.getCurrentUserIdOrNull(session); // 로그인 시 사용자 ID, 아니면 null
        return ResponseEntity.ok(reviewService.list(aniId, currentUserId, sort, page, size)); // 200 OK + 본문
    }

    @PostMapping // HTTP POST /api/anime/{aniId}/reviews
    public ResponseEntity<Long> create( // 생성된 리뷰의 PK(ID)를 반환
            @PathVariable Long aniId, // 경로 변수: 애니 ID
            @Valid @RequestBody CreateReviewRequestDto dto,
            HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = authUtil.requireCurrentUserId(session); // 로그인 필수
        Long id = reviewService.create(userId, aniId, dto.getContent(), dto.getRating()); // 서비스 호출
        return ResponseEntity.ok(id); // 200 OK + 리뷰 ID
    }

    @DeleteMapping // DELETE /api/anime/{aniId]/reviews
    public ResponseEntity<Void> deleteAllByAni( // 특정 애니의 리뷰 일괄 삭제(관리용)
            @PathVariable Long aniId // 경로변수: 애니 ID
    ) {
        reviewService.deleteHardByAniList(aniId); // 일괄 하드 삭제
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @PutMapping("/api/reviews/{reviewId}") // 절대 경로: PUT
    public ResponseEntity<Void> update( // 본인 리뷰 수정
            @PathVariable Long reviewId, // 경로변수: 리뷰 ID
            @Valid @RequestBody UpdateReviewRequestDto dto, // 요청 바디: 수정 필드(content/rating)
            HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = authUtil.requireCurrentUserId(session); // 로그인 필수
        reviewService.update(reviewId, userId, dto.getContent(), dto.getRating()); // 서비스 위임
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @DeleteMapping("/api/reviews/{reviewId}") // 절대 경로: DELETE
    public ResponseEntity<Void> delete( // 본인 리뷰 소프트 삭제(상태 전환)
            @PathVariable Long reviewId, // 경로변수: 리뷰 ID
            HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = authUtil.requireCurrentUserId(session); // 로그인 필수
        reviewService.deleteSoft(reviewId, userId); // 상태 DELETED 전환
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @PostMapping("/api/reviews/{reviewId}/report") // 절대 경로 Post
    public ResponseEntity<Void> report( // 리뷰 신고
            @PathVariable Long reviewId, // 경로변수: 리뷰 ID
            HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = authUtil.requireCurrentUserId(session); // 로그인 필수
        reviewService.report(reviewId, userId); // 상태 REPORTED 전환
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @PostMapping("/api/reviews/{reviewId}/like") // 절대 경로: POST
    public ResponseEntity<Boolean> toggleLike( // 좋아요 토글(true=on, false=off)
            @PathVariable Long reviewId, // 경로변수: 리뷰 ID
            HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = authUtil.requireCurrentUserId(session); // 로그인 필수
        return ResponseEntity.ok(reviewService.toggleLike(reviewId, userId)); // 200 OK + 토글 결과
    }
}
