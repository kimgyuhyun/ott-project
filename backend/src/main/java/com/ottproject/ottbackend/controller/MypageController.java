package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.MypageStatsDto;
import com.ottproject.ottbackend.service.MypageService;
import com.ottproject.ottbackend.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}


