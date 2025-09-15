package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.service.RecentAnimeService;
import com.ottproject.ottbackend.util.SecurityUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * 최근본 애니메이션 컨트롤러
 *
 * 큰 흐름
 * - 사용자의 최근본 목록을 관리한다.
 * - 삭제 시 시청 기록은 유지하고 최근본 목록에서만 숨김 처리한다.
 *
 * 엔드포인트 개요
 * - DELETE /api/mypage/recent/anime/{aniId}: 최근본 목록에서 숨김 처리
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class RecentAnimeController {
    
    private final RecentAnimeService recentAnimeService;
    private final SecurityUtil securityUtil;
    
    @Operation(summary = "최근본 목록에서 숨김", description = "특정 애니메이션을 최근본 목록에서 숨김 처리합니다. 시청 기록은 유지됩니다.")
    @ApiResponse(responseCode = "200", description = "숨김 처리 성공")
    @DeleteMapping("/mypage/recent/anime/{aniId}")
    public ResponseEntity<Void> hideFromRecent(
            @PathVariable Long aniId,
            HttpSession session
    ) {
        System.out.println("🎯 [CONTROLLER] 최근본 목록에서 숨김 요청 - aniId: " + aniId);
        
        Long userId = securityUtil.requireCurrentUserId(session);
        System.out.println("🎯 [CONTROLLER] 인증된 사용자 ID: " + userId);
        
        recentAnimeService.hideFromRecent(userId, aniId);
        System.out.println("🎯 [CONTROLLER] 최근본 목록에서 숨김 처리 완료");
        
        return ResponseEntity.ok().build();
    }
    
    @Operation(summary = "정주행 목록에서 완전 삭제", description = "특정 애니메이션을 정주행 목록에서 완전 삭제합니다. 시청 기록이 완전히 삭제됩니다.")
    @ApiResponse(responseCode = "200", description = "완전 삭제 성공")
    @DeleteMapping("/mypage/binge/anime/{aniId}")
    public ResponseEntity<Void> deleteFromBinge(
            @PathVariable Long aniId,
            HttpSession session
    ) {
        System.out.println("🎯 [CONTROLLER] 정주행 목록에서 완전 삭제 요청 - aniId: " + aniId);
        
        Long userId = securityUtil.requireCurrentUserId(session);
        System.out.println("🎯 [CONTROLLER] 인증된 사용자 ID: " + userId);
        
        recentAnimeService.deleteFromBinge(userId, aniId);
        System.out.println("🎯 [CONTROLLER] 정주행 목록에서 완전 삭제 완료");
        
        return ResponseEntity.ok().build();
    }
}
