package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.admin.AdminEpisodeDetailDto;
import com.ottproject.ottbackend.dto.admin.EpisodeCreateRequest;
import com.ottproject.ottbackend.dto.admin.EpisodeUpdateRequest;
import com.ottproject.ottbackend.service.AdminEpisodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자 에피소드 관리 컨트롤러
 *
 * 큰 흐름
 * - 작품에 새 화수를 등록한다. 등록되면 그 작품을 찜한 사용자에게 업데이트 알림이 나간다.
 *   (그 전까지 에피소드는 시드 SQL 로만 들어와서 알림 트리거가 한 번도 호출되지 않았다.)
 *
 * 인가
 * - SecurityConfig 의 "/api/admin/**" → hasRole("ADMIN") 규칙이 이 컨트롤러를 덮는다.
 * - 이 프로젝트에는 @EnableMethodSecurity 가 없어 @PreAuthorize 는 조용히 무시된다. 붙이지 말 것.
 * - "/api/admin/public/**" 은 permitAll 이며 ADMIN 규칙보다 위에 있다. 이 컨트롤러의 경로를 그 아래로
 *   옮기면 즉시 전세계 공개가 된다.
 *
 * 엔드포인트 개요
 * - POST /{animeId}/episodes: 에피소드 등록
 * - GET /{animeId}/episodes: 화수 목록 조회
 * - PATCH /{animeId}/episodes/{episodeId}: 화수 부분 수정
 */
@RestController
@RequestMapping("/api/admin/animes")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "관리자 에피소드 관리", description = "에피소드 등록 API")
public class AdminEpisodeController {

    private final AdminEpisodeService adminEpisodeService;

    @Operation(summary = "에피소드 등록",
            description = "작품에 새 화수를 등록하고, 해당 작품을 찜한 사용자에게 업데이트 알림을 발송합니다.")
    @ApiResponse(responseCode = "200", description = "등록 성공")
    @ApiResponse(responseCode = "400", description = "필수값 누락 또는 이미 등록된 화수")
    @ApiResponse(responseCode = "404", description = "애니메이션 없음")
    @PostMapping("/{animeId}/episodes")
    public ResponseEntity<AdminEpisodeDetailDto> createEpisode(
            @Parameter(description = "애니메이션 ID", required = true) @PathVariable Long animeId,
            @Parameter(description = "에피소드 등록 정보", required = true) @RequestBody EpisodeCreateRequest request) {

        return ResponseEntity.ok(adminEpisodeService.createEpisode(animeId, request));
    }

    @Operation(summary = "에피소드 목록 조회", description = "작품의 화수 목록을 화수 오름차순으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "애니메이션 없음")
    @GetMapping("/{animeId}/episodes")
    public ResponseEntity<List<AdminEpisodeDetailDto>> listEpisodes(
            @Parameter(description = "애니메이션 ID", required = true) @PathVariable Long animeId) {

        return ResponseEntity.ok(adminEpisodeService.listEpisodes(animeId));
    }

    @Operation(summary = "에피소드 수정",
            description = "영상 URL/썸네일/제목/활성·공개 여부를 수정합니다. 전달하지 않은 필드는 변경하지 않습니다. "
                    + "화수(episodeNumber)는 시청 기록이 붙어 있어 변경 대상이 아닙니다.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "400", description = "빈 값으로 수정 시도")
    @ApiResponse(responseCode = "404", description = "에피소드 없음 또는 해당 작품 소속이 아님")
    @PatchMapping("/{animeId}/episodes/{episodeId}")
    public ResponseEntity<AdminEpisodeDetailDto> updateEpisode(
            @Parameter(description = "애니메이션 ID", required = true) @PathVariable Long animeId,
            @Parameter(description = "에피소드 ID", required = true) @PathVariable Long episodeId,
            @Parameter(description = "수정할 값", required = true) @RequestBody EpisodeUpdateRequest request) {

        return ResponseEntity.ok(adminEpisodeService.updateEpisode(animeId, episodeId, request));
    }
}
