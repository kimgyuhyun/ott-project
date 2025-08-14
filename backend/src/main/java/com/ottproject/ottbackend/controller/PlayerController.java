package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.EpisodeProgressRequestDto;
import com.ottproject.ottbackend.dto.BulkProgressRequestDto;
import com.ottproject.ottbackend.dto.StreamUrlResponseDto;
import com.ottproject.ottbackend.service.PlayerAuthService;
import com.ottproject.ottbackend.service.ProgressService;
import com.ottproject.ottbackend.util.AuthUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;

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
    private final PlayerAuthService auth; private final ProgressService progress; private final AuthUtil authUtil; // 의존성 주입

    /**
     * 에피소드 재생용 서명된 스트림 URL 발급
     * - 세션에서 사용자 식별, 권한 검사 실패 시 403
     * - 성공 시 secure_link 파라미터가 포함된 m3u8 URL 반환
     */
    @GetMapping("/api/episodes/{id}/stream-url") // 서명 URL 발급 엔드포인트
    public ResponseEntity<StreamUrlResponseDto> streamUrl(@PathVariable Long id, HttpSession session) { // 경로 변수/세션 입력
        Long userId = authUtil.requireCurrentUserId(session); // 세션에서 사용자 ID 획득(미로그인 시 401)
        if (!auth.canStream(userId, id)) return ResponseEntity.status(403).build(); // 재생 권한 없으면 403
        String url = auth.buildSignedStreamUrl(userId, id); // secure_link 서명 URL 생성
        return ResponseEntity.ok(StreamUrlResponseDto.builder().url(url).build()); // 바디에 URL 담아 200 OK
    }

    /**
     * 시청 진행률 저장(멱등 upsert)
     */
    @PostMapping("/api/episodes/{id}/progress") // 진행률 저장 엔드포인트
    public ResponseEntity<Void> saveProgress(@PathVariable Long id, @Valid @RequestBody EpisodeProgressRequestDto body, HttpSession session) { // 검증 적용
        Long userId = authUtil.requireCurrentUserId(session); // 세션 사용자 확인
        progress.upsert(userId, id, body.getPositionSec(), body.getDurationSec()); // 진행 위치/총 길이 저장(업서트)
        return ResponseEntity.ok().build(); // 본문 없는 200 OK
    }

    /**
     * 단건 진행률 조회(상세/카드 노출용)
     */
    @GetMapping("/api/episodes/{id}/progress") // 진행률 단건 조회
    public ResponseEntity<?> getProgress(@PathVariable Long id, HttpSession session) { // 에피소드 ID 입력
        Long userId = authUtil.requireCurrentUserId(session); // 사용자 확인
        return ResponseEntity.ok(progress.find(userId, id).orElse(null)); // 없으면 null 반환
    }

    /**
     * 진행률 벌크 조회(목록/카드용)
     * - 존재하는 진행률만 맵에 포함
     */
    @PostMapping("/api/episodes/progress") // 벌크 조회 엔드포인트
    public ResponseEntity<java.util.Map<Long, Object>> getProgressBulk(@Valid @RequestBody BulkProgressRequestDto body, HttpSession session) { // ID 목록 입력
        Long userId = authUtil.requireCurrentUserId(session); // 사용자 확인
        var result = progress.findBulk(userId, body.getEpisodeIds()); // 벌크 조회 수행
        return ResponseEntity.ok(new java.util.HashMap<>(result)); // 결과 맵 반환
    }

    /**
     * 다음 화 ID 조회(자동재생용)
     */
    @GetMapping("/api/episodes/{id}/next") // 다음 화 조회
    public ResponseEntity<Long> nextEpisode(@PathVariable Long id) { // 현재 화 ID 입력
        Long nextId = auth.nextEpisodeId(id); // 다음 화 탐색
        return (nextId != null) ? ResponseEntity.ok(nextId) : ResponseEntity.noContent().build(); // 없으면 204
    }
}