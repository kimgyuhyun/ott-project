package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.EpisodeDto;
import com.ottproject.ottbackend.dto.EpisodeProgressResponseDto;
import com.ottproject.ottbackend.service.PlayerService;
import com.ottproject.ottbackend.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

/**
 * EpisodeController
 *
 * 큰 흐름
 * - 플레이어 도메인에 필요한 모든 API 엔드포인트를 제공한다.
 * - 다음 에피소드 조회, 스트림 URL 발급, 진행률 관리 등을 담당한다.
 *
 * 엔드포인트 개요
 * - GET /api/episodes/{id}/next: 다음 에피소드 조회
 * - GET /api/episodes/{id}/stream-url: 스트림 URL 발급
 * - POST /api/episodes/{id}/progress: 시청 진행률 저장
 * - GET /api/episodes/{id}/progress: 시청 진행률 조회
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/episodes")
public class EpisodeController {
    
    private final PlayerService playerService;
    private final SecurityUtil securityUtil;
    
    @Operation(summary = "다음 에피소드 조회", description = "현재 에피소드의 다음 에피소드 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "다음 에피소드 없음")
    @GetMapping("/{id}/next")
    public ResponseEntity<EpisodeDto> getNextEpisode(
            @Parameter(description = "에피소드 ID") @PathVariable Long id) {
        EpisodeDto nextEpisode = playerService.getNextEpisode(id);
        if (nextEpisode == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(nextEpisode);
    }
    
    @Operation(summary = "스트림 URL 발급", description = "에피소드의 서명된 스트림 URL을 발급합니다.")
    @ApiResponse(responseCode = "200", description = "발급 성공")
    @ApiResponse(responseCode = "403", description = "재생 권한 없음")
    @GetMapping("/{id}/stream-url")
    public ResponseEntity<Map<String, String>> getStreamUrl(
            @Parameter(description = "에피소드 ID") @PathVariable Long id,
            HttpSession session) {
        Long userId = securityUtil.getCurrentUserIdOrNull(session);
        if (userId == null) {
            return ResponseEntity.status(403).body(Map.of("error", "로그인이 필요합니다."));
        }
        
        if (!playerService.canStream(userId, id)) {
            return ResponseEntity.status(403).body(Map.of("error", "재생 권한이 없습니다."));
        }
        
        String streamUrl = playerService.getStreamUrl(userId, id);
        return ResponseEntity.ok(Map.of("url", streamUrl));
    }
    
    @Operation(summary = "시청 진행률 저장", description = "에피소드 시청 진행률을 저장합니다.")
    @ApiResponse(responseCode = "200", description = "저장 성공")
    @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터")
    @PostMapping("/{id}/progress")
    public ResponseEntity<Void> saveProgress(
            @Parameter(description = "에피소드 ID") @PathVariable Long id,
            @RequestBody Map<String, Integer> request,
            HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        Integer positionSec = request.get("positionSec");
        Integer durationSec = request.get("durationSec");
        
        // 입력 데이터 검증
        if (positionSec == null || durationSec == null) {
            return ResponseEntity.badRequest().build();
        }
        
        if (positionSec < 0 || durationSec <= 0 || positionSec > durationSec) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            playerService.saveProgress(userId, id, positionSec, durationSec);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // 로그 기록 후 500 에러 반환
            System.err.println("진행률 저장 실패: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @Operation(summary = "시청 진행률 조회", description = "에피소드 시청 진행률을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{id}/progress")
    public ResponseEntity<Map<String, Object>> getProgress(
            @Parameter(description = "에피소드 ID") @PathVariable Long id,
            HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        var progress = playerService.getProgress(userId, id);
        
        if (progress.isEmpty()) {
            return ResponseEntity.ok(Map.of("positionSec", 0, "durationSec", 0));
        }
        
        var progressData = progress.get();
        return ResponseEntity.ok(Map.of(
            "positionSec", progressData.getPositionSec(),
            "durationSec", progressData.getDurationSec()
        ));
    }
    
    @Operation(summary = "여러 에피소드 진행률 벌크 조회", description = "여러 에피소드의 시청 진행률을 일괄 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @PostMapping("/progress")
    public ResponseEntity<Map<Long, EpisodeProgressResponseDto>> getBulkProgress(
            @RequestBody Map<String, List<Long>> request,
            HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        List<Long> episodeIds = request.get("episodeIds");
        
        if (episodeIds == null || episodeIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        Map<Long, EpisodeProgressResponseDto> progress = playerService.getBulkProgress(userId, episodeIds);
        return ResponseEntity.ok(progress);
    }
    
    @Operation(summary = "마이페이지 시청 기록 조회", description = "사용자의 시청 기록을 페이지네이션으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/mypage/watch-history")
    public ResponseEntity<Map<String, Object>> getWatchHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        
        var watchHistory = playerService.getWatchHistory(userId, page, size);
        return ResponseEntity.ok(watchHistory);
    }
}
