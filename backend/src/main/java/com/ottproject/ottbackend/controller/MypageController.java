package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.MypageStatsDto;
import com.ottproject.ottbackend.dto.MyRatingItemDto;
import com.ottproject.ottbackend.dto.MyReviewItemDto;
import com.ottproject.ottbackend.dto.MyCommentItemDto;
import com.ottproject.ottbackend.service.MypageService;
import com.ottproject.ottbackend.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
public class MypageController {

    private final MypageService mypageService;
    private final SecurityUtil securityUtil;

    @Operation(summary = "마이페이지 집계 조회", description = "사용자의 보고싶다/리뷰/댓글 집계를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/stats")
    public ResponseEntity<MypageStatsDto> getStats(HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        MypageStatsDto dto = mypageService.getMypageStats(userId);
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "내 별점 목록", description = "페이지/사이즈 기준으로 내 별점 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/ratings")
    public ResponseEntity<java.util.List<MyRatingItemDto>> getMyRatings(
            HttpSession session,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        Long userId = securityUtil.requireCurrentUserId(session);
        return ResponseEntity.ok(mypageService.getMyRatings(userId, page, size));
    }

    @Operation(summary = "내 리뷰 목록", description = "페이지/사이즈 기준으로 내 리뷰 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/reviews")
    public ResponseEntity<java.util.List<MyReviewItemDto>> getMyReviews(
            HttpSession session,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        Long userId = securityUtil.requireCurrentUserId(session);
        return ResponseEntity.ok(mypageService.getMyReviews(userId, page, size));
    }

    @Operation(summary = "내 댓글 목록", description = "페이지/사이즈 기준으로 내 댓글 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/comments")
    public ResponseEntity<java.util.List<MyCommentItemDto>> getMyComments(
            HttpSession session,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        Long userId = securityUtil.requireCurrentUserId(session);
        return ResponseEntity.ok(mypageService.getMyComments(userId, page, size));
    }
}


