package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.EpisodeProgressRequestDto;
import com.ottproject.ottbackend.dto.BulkProgressRequestDto;
import com.ottproject.ottbackend.dto.PlayerStreamUrlResponseDto;
import com.ottproject.ottbackend.dto.EpisodeProgressResponseDto;
import com.ottproject.ottbackend.service.PlaybackAuthService;
import com.ottproject.ottbackend.service.PlayerProgressService;
import com.ottproject.ottbackend.util.SecurityUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/**
 * 플레이어 관련 API 컨트롤러
 * - 스트림 URL 발급(secure_link 서명)
 * - 시청 진행률 저장/조회(단건/벌크)
 * - 다음 화 조회(자동재생)
 */
@RestController // REST 컨트롤러 등록
@RequiredArgsConstructor // 생성자 주입 자동 생성
@Validated // 요청 검증 활성화
public class PlayerController { // 스트리밍/진행률
    private final PlaybackAuthService auth; // 스트림 권한 검사 및 서명 URL 생성 서비스
    private final PlayerProgressService progress; // 시청 진행률 저장/조회 서비스
    private final SecurityUtil securityUtil; // 세션에서 사용자 ID 확인 유틸

    /**
     * 에피소드 재생용 서명된 스트림 URL 발급
     * - 세션에서 사용자 식별, 권한 검사 실패 시 403
     * - 성공 시 secure_link 파라미터가 포함된 m3u8 URL 반환
     */
    @Operation(
            summary = "서명된 스트림 URL 발급",
            description = "세션의 사용자 권한을 검사한 뒤 secure_link 파라미터가 포함된 m3u8 URL을 반환합니다. 1화는 비멤버 720p, 이후 화는 멤버십 필요.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(schema = @Schema(implementation = PlayerStreamUrlResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "미인증"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @GetMapping("/api/episodes/{id}/stream-url") // 서명 URL 발급 엔드포인트
    public ResponseEntity<PlayerStreamUrlResponseDto> streamUrl(
            @Parameter(description = "에피소드 ID", required = true) @PathVariable Long id,
            HttpSession session) { // 경로 변수/세션 입력
        Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 사용자 ID 획득(미로그인 시 401)
        if (!auth.canStream(userId, id)) return ResponseEntity.status(403).build(); // 재생 권한 없으면 403
        String url = auth.buildSignedStreamUrl(userId, id); // secure_link 서명 URL 생성
        return ResponseEntity.ok(PlayerStreamUrlResponseDto.builder().url(url).build()); // 바디에 URL 담아 200 OK
    }

    /**
     * 시청 진행률 저장(멱등 upsert)
     */
    @Operation(summary = "진행률 저장", description = "에피소드의 현재 시청 위치와 총 길이를 저장합니다. 멱등 upsert.")
    @ApiResponse(responseCode = "200", description = "저장 완료: 최신 진행률 반환")
    @PostMapping("/api/episodes/{id}/progress") // 진행률 저장 엔드포인트
    public ResponseEntity<EpisodeProgressResponseDto> saveProgress(
            @Parameter(description = "에피소드 ID", required = true) @PathVariable Long id,
            @Valid @RequestBody EpisodeProgressRequestDto body, HttpSession session) { // 검증 적용
        Long userId = securityUtil.requireCurrentUserId(session); // 세션 사용자 확인
        progress.upsert(userId, id, body.getPositionSec(), body.getDurationSec()); // 진행 위치/총 길이 저장(업서트)
        return ResponseEntity.ok(progress.find(userId, id).orElse(null)); // 저장 직후 최신 진행률 반환
    }

    /**
     * 단건 진행률 조회(상세/카드 노출용)
     */
    @Operation(summary = "진행률 단건 조회", description = "특정 에피소드에 대한 시청 진행률을 조회합니다. 없으면 null 반환.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/api/episodes/{id}/progress") // 진행률 단건 조회
    public ResponseEntity<?> getProgress(
            @Parameter(description = "에피소드 ID", required = true) @PathVariable Long id,
            HttpSession session) { // 에피소드 ID 입력
        Long userId = securityUtil.requireCurrentUserId(session); // 사용자 확인
        return ResponseEntity.ok(progress.find(userId, id).orElse(null)); // 없으면 null 반환
    }

    /**
     * 진행률 벌크 조회(목록/카드용)
     * - 존재하는 진행률만 맵에 포함
     */
    @Operation(summary = "진행률 벌크 조회", description = "요청한 에피소드 ID 목록 중 존재하는 진행률만 key-value로 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @PostMapping("/api/episodes/progress") // 벌크 조회 엔드포인트
    public ResponseEntity<java.util.Map<Long, Object>> getProgressBulk(
            @Valid @RequestBody BulkProgressRequestDto body, HttpSession session) { // ID 목록 입력
        Long userId = securityUtil.requireCurrentUserId(session); // 사용자 확인
        var result = progress.findBulk(userId, body.getEpisodeIds()); // 벌크 조회 수행
        return ResponseEntity.ok(new java.util.HashMap<>(result)); // 결과 맵 반환
    }

    /**
     * 다음 화 ID 조회(자동재생용)
     */
    @Operation(summary = "다음 화 조회", description = "현재 화 기준 다음 화의 ID를 반환합니다. 없으면 204 No Content.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "다음 화 존재"),
            @ApiResponse(responseCode = "204", description = "다음 화 없음")
    })
    @GetMapping("/api/episodes/{id}/next") // 다음 화 조회
    public ResponseEntity<Long> nextEpisode(
            @Parameter(description = "현재 에피소드 ID", required = true) @PathVariable Long id) { // 현재 화 ID 입력
        Long nextId = auth.nextEpisodeId(id); // 다음 화 탐색
        return (nextId != null) ? ResponseEntity.ok(nextId) : ResponseEntity.noContent().build(); // 없으면 204
    }
}