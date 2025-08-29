package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.SkipMetaResponseDto;
import com.ottproject.ottbackend.dto.SkipUsageRequestDto;
import com.ottproject.ottbackend.service.PlayerService;
import com.ottproject.ottbackend.util.SecurityUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * PlayerSkipController
 *
 * 큰 흐름
 * - 에피소드의 스킵 메타 조회와 스킵 사용 로깅을 제공한다.
 *
 * 엔드포인트 개요
 * - GET /api/episodes/{id}/skips: 스킵 메타 조회
 * - POST /api/episodes/{id}/skips/track: 스킵 사용 로깅(비로그인 허용)
 */
@RestController // REST 컨트롤러
@RequiredArgsConstructor // 생성자 주입
@org.springframework.web.bind.annotation.RequestMapping("/api/episodes")
public class PlayerSkipController { // 스킵 메타
	private final PlayerService playerService; // 통합 플레이어 서비스
	private final SecurityUtil securityUtil; // 세션 사용자 식별 유틸

	/**
	 * 에피소드 스킵 메타 조회
	 */
    @Operation(summary = "스킵 메타 조회", description = "에피소드의 오프닝/엔딩 스킵 구간 메타를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{id}/skips")
	public ResponseEntity<SkipMetaResponseDto> get(@PathVariable Long id) { // 에피소드 ID 입력
		return ResponseEntity.ok(playerService.getSkipMetaByEpisode(id)); // 메타 반환
	}

	/**
	 * 스킵 사용/자동스킵 로깅(비로그인 허용)
	 */
    @Operation(summary = "스킵 트래핑", description = "스킵 버튼 사용/자동 스킵 시점을 기록합니다. 비로그인 허용.")
    @ApiResponse(responseCode = "202", description = "기록 접수")
    @PostMapping("/{id}/skips/track") // 스킵 사용 수집
	public ResponseEntity<Void> track(@PathVariable Long id, @Valid @RequestBody SkipUsageRequestDto body, HttpSession session) { // 요청 바디 검증
		Long userId = securityUtil.getCurrentUserIdOrNull(session); // 로그인 시 사용자 ID, 아니면 null
		playerService.trackUsage(userId, id, body.getType(), body.getAtSec()); // DB 적재
		return ResponseEntity.accepted().build(); // 202 Accepted
	}
}