package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.SubtitleDto;
import com.ottproject.ottbackend.dto.SkipMetaResponseDto;
import com.ottproject.ottbackend.dto.UserSettingsDto;
import com.ottproject.ottbackend.service.PlayerService;
import com.ottproject.ottbackend.service.SettingsService;
import com.ottproject.ottbackend.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PlayerController
 *
 * 큰 흐름
 * - 플레이어에 필요한 자막, 스킵 정보를 제공하는 API를 제공한다.
 *
 * 엔드포인트 개요
 * - GET /api/player/episodes/{episodeId}/subtitles: 자막 목록 조회
 * - GET /api/player/episodes/{episodeId}/subtitles/default: 기본 자막 조회
 * - GET /api/player/episodes/{episodeId}/subtitles/{language}: 특정 언어 자막 조회
 * - GET /api/player/episodes/{episodeId}/skips: 스킵 정보 조회
 * - GET /api/player/users/me/settings: 사용자 재생 설정 조회 (SettingsService 연동)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/player")
public class PlayerController {
    private final PlayerService playerService;
    private final SettingsService settingsService;
    private final SecurityUtil securityUtil;

    @Operation(summary = "자막 목록 조회", description = "에피소드의 모든 자막 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/episodes/{episodeId}/subtitles")
    public ResponseEntity<List<SubtitleDto>> getSubtitles(
            @Parameter(description = "에피소드 ID") @PathVariable Long episodeId
    ) {
        List<SubtitleDto> subtitles = playerService.getSubtitlesByEpisode(episodeId);
        return ResponseEntity.ok(subtitles);
    }

    @Operation(summary = "기본 자막 조회", description = "에피소드의 기본 자막을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/episodes/{episodeId}/subtitles/default")
    public ResponseEntity<SubtitleDto> getDefaultSubtitle(
            @Parameter(description = "에피소드 ID") @PathVariable Long episodeId
    ) {
        SubtitleDto subtitle = playerService.getDefaultSubtitle(episodeId);
        if (subtitle == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(subtitle);
    }

    @Operation(summary = "언어별 자막 조회", description = "특정 언어의 자막을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/episodes/{episodeId}/subtitles/{language}")
    public ResponseEntity<SubtitleDto> getSubtitleByLanguage(
            @Parameter(description = "에피소드 ID") @PathVariable Long episodeId,
            @Parameter(description = "언어 코드") @PathVariable String language
    ) {
        SubtitleDto subtitle = playerService.getSubtitleByLanguage(episodeId, language);
        if (subtitle == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(subtitle);
    }

    @Operation(summary = "스킵 정보 조회", description = "에피소드의 오프닝/엔딩 스킵 구간을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/episodes/{episodeId}/skips")
    public ResponseEntity<SkipMetaResponseDto> getSkips(
            @Parameter(description = "에피소드 ID") @PathVariable Long episodeId
    ) {
        SkipMetaResponseDto skip = playerService.getSkipMetaByEpisode(episodeId);
        if (skip == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(skip);
    }

    @Operation(summary = "사용자 재생 설정 조회", description = "현재 사용자의 재생 환경 설정을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/users/me/settings")
    public ResponseEntity<UserSettingsDto> getUserSettings(HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        UserSettingsDto settings = settingsService.get(userId);
        if (settings == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(settings);
    }
}