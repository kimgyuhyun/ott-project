package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.admin.AdminEpisodeDetailDto;
import com.ottproject.ottbackend.dto.admin.EpisodeCreateRequest;
import com.ottproject.ottbackend.service.AdminEpisodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
