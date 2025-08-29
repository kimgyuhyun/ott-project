package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.SubtitleDto;
import com.ottproject.ottbackend.service.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SubtitleController
 *
 * 큰 흐름
 * - 에피소드별 자막 정보를 조회하는 API를 제공한다.
 *
 * 엔드포인트 개요
 * - GET /api/episodes/{episodeId}/subtitles: 자막 목록 조회
 * - GET /api/episodes/{episodeId}/subtitles/default: 기본 자막 조회
 * - GET /api/episodes/{episodeId}/subtitles/{language}: 특정 언어 자막 조회
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/episodes")
public class SubtitleController {
    private final PlayerService playerService;

    @Operation(summary = "자막 목록 조회", description = "에피소드의 모든 자막 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{episodeId}/subtitles")
    public ResponseEntity<List<SubtitleDto>> getSubtitles(
            @Parameter(description = "에피소드 ID") @PathVariable Long episodeId
    ) {
        List<SubtitleDto> subtitles = playerService.getSubtitlesByEpisode(episodeId);
        return ResponseEntity.ok(subtitles);
    }

    @Operation(summary = "기본 자막 조회", description = "에피소드의 기본 자막을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{episodeId}/subtitles/default")
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
    @GetMapping("/{episodeId}/subtitles/{language}")
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
}
