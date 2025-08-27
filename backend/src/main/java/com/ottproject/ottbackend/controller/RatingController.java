package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.service.RatingService;
import com.ottproject.ottbackend.util.SecurityUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * RatingController
 *
 * 큰 흐름
 * - 애니메이션 평점 생성/수정/조회/삭제를 제공한다.
 *
 * 엔드포인트 개요
 * - POST /api/anime/{aniId}/ratings: 평점 생성/수정
 * - GET /api/anime/{aniId}/ratings/me: 내 평점 조회
 * - GET /api/anime/{aniId}/ratings/stats: 평점 통계 조회
 * - DELETE /api/anime/{aniId}/ratings: 내 평점 삭제
 */
@Tag(name = "평점", description = "애니메이션 평점 관리 API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/anime/{aniId}/ratings")
public class RatingController {

    private final RatingService ratingService;
    private final SecurityUtil securityUtil;

    @Operation(summary = "평점 생성/수정", description = "애니메이션에 대한 평점을 생성하거나 기존 평점을 수정합니다.")
    @ApiResponse(responseCode = "200", description = "평점 생성/수정 성공")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @PostMapping
    public ResponseEntity<Double> createOrUpdate(
            @Parameter(description = "애니메이션 ID", required = true) 
            @PathVariable Long aniId, 
            @Parameter(description = "평점 (1.0 ~ 5.0)", required = true) 
            @RequestParam Double score, 
            HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        ratingService.createOrUpdateRating(userId, aniId, score);
        return ResponseEntity.ok(score);
    }

    @Operation(summary = "내 평점 조회", description = "현재 로그인한 사용자의 특정 애니메이션에 대한 평점을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @GetMapping("/me")
    public ResponseEntity<Double> myRating(
            @Parameter(description = "애니메이션 ID", required = true) 
            @PathVariable Long aniId, 
            HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        return ResponseEntity.ok(ratingService.getUserRating(userId, aniId));
    }

    @Operation(summary = "평점 통계 조회", description = "애니메이션의 평점 분포 및 평균 평점을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/stats")
    public ResponseEntity<java.util.Map<String, Object>> stats(
            @Parameter(description = "애니메이션 ID", required = true) 
            @PathVariable Long aniId) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("distribution", ratingService.getDistribution(aniId));
        body.put("average", ratingService.getAverage(aniId));
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "내 평점 삭제", description = "현재 로그인한 사용자의 특정 애니메이션에 대한 평점을 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "삭제 성공")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @DeleteMapping
    public ResponseEntity<Void> delete(
            @Parameter(description = "애니메이션 ID", required = true) 
            @PathVariable Long aniId, 
            HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        ratingService.deleteMyRating(userId, aniId);
        return ResponseEntity.noContent().build();
    }
}


