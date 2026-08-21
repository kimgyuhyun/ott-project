package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.BingeWatchDto;
import com.ottproject.ottbackend.service.BingeWatchService;
import com.ottproject.ottbackend.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 정주행 컨트롤러
 *
 * 큰 흐름
 * - 사용자의 정주행 완료 작품 목록을 조회한다.
 * - 완결 작품 중 모든 에피소드를 90% 이상 시청한 작품을 정주행으로 간주한다.
 *
 * 엔드포인트 개요
 * - GET /api/mypage/binge: 정주행 완료 작품 목록 조회
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class BingeWatchController {

    private final BingeWatchService bingeWatchService;
    private final SecurityUtil securityUtil;

    @Operation(summary = "정주행 완료 작품 목록", description = "사용자의 정주행 완료 작품 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/mypage/binge")
    public ResponseEntity<List<BingeWatchDto>> getBingeWatchedAnimes(HttpSession session) {
        System.out.println("🎯 [CONTROLLER] 정주행 완료 작품 목록 조회 요청");

        Long userId = securityUtil.requireCurrentUserId(session);
        System.out.println("🎯 [CONTROLLER] 인증된 사용자 ID: " + userId);

        List<BingeWatchDto> result = bingeWatchService.getBingeWatchedAnimes(userId);
        System.out.println("🎯 [CONTROLLER] 서비스 응답 - 정주행 완료 작품 수: " + result.size());

        return ResponseEntity.ok(result);
    }
}
