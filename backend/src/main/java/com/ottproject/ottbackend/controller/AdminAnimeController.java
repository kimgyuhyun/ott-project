package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.service.AnimeEnhancementService;
import com.ottproject.ottbackend.service.SimpleAnimeDataCollectorService;
import com.ottproject.ottbackend.service.SimpleAnimeDataCollectorService.CollectionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 애니메이션 관리 컨트롤러
 *
 * 큰 흐름
 * - 애니메이션에 대한 관리자 작업을 한 곳에서 제공한다: 외부 동기화(Jikan), 데이터 보강(TMDB).
 * - 이전에는 SimpleAdminAnimeSyncController 와 AnimeEnhancementController 가 같은 /api/admin/anime 를
 *   각각 매핑하고 있었다. 한 관심사가 두 파일로 쪼개져 있어 새 엔드포인트를 어디에 둘지가 매번 임의 선택이었다.
 *
 * 인가
 * - SecurityConfig 의 "/api/admin/**" → hasRole("ADMIN") 규칙이 이 컨트롤러 전체를 덮는다.
 * - 이 프로젝트에는 @EnableMethodSecurity 가 없어 @PreAuthorize 는 조용히 무시된다. 붙이지 말 것.
 *   (보호처럼 보이지만 아무것도 막지 못한다.)
 * - "/api/admin/public/**" 은 permitAll 이며 ADMIN 규칙보다 위에 있다. 이 컨트롤러의 경로를 그 아래로
 *   옮기면 즉시 전세계 공개가 된다.
 *
 * 엔드포인트 개요
 * - POST /sync/{malId}: 단일 애니메이션 동기화
 * - POST /sync-popular: 인기 애니메이션 일괄 동기화
 * - POST /enhance-all: 전체 TMDB 보강(비동기)
 * - POST /enhance/{animeId}: 단건 TMDB 보강
 * - GET /enhancement-status: 보강 서비스 헬스체크
 */
@RestController
@RequestMapping("/api/admin/anime")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "관리자 애니메이션 관리", description = "Jikan 동기화 / TMDB 보강 API")
public class AdminAnimeController {

    private final SimpleAnimeDataCollectorService collectorService;
    private final AnimeEnhancementService animeEnhancementService;

    // ===== Jikan 동기화 =====

    /**
     * 단일 애니메이션 동기화
     */
    @Operation(summary = "단일 애니메이션 동기화", description = "특정 MAL ID의 애니메이션을 Jikan API에서 수집하여 DB에 저장합니다.")
    @ApiResponse(responseCode = "200", description = "동기화 성공")
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
    @PostMapping("/sync/{malId}")
    public ResponseEntity<SyncResult> syncAnime(
            @Parameter(description = "MyAnimeList 애니메이션 ID", required = true)
            @PathVariable Long malId) {

        log.info("단일 애니메이션 동기화 요청: MAL ID {}", malId);

        try {
            boolean success = collectorService.collectAnime(malId);

            if (success) {
                log.info("애니메이션 동기화 성공: MAL ID {}", malId);
                return ResponseEntity.ok(new SyncResult(true, "동기화 성공", malId));
            } else {
                log.warn("애니메이션 동기화 실패: MAL ID {}", malId);
                return ResponseEntity.ok(new SyncResult(false, "동기화 실패 (이미 존재하거나 19금 콘텐츠)", malId));
            }

        } catch (Exception e) {
            log.error("애니메이션 동기화 중 오류 발생: MAL ID {}", malId, e);
            return ResponseEntity.badRequest()
                    .body(new SyncResult(false, "동기화 중 오류 발생: " + e.getMessage(), malId));
        }
    }

    /**
     * 인기 애니메이션 일괄 동기화
     */
    @Operation(summary = "인기 애니메이션 일괄 동기화", description = "Jikan API의 인기 애니메이션 목록을 수집하여 DB에 저장합니다.")
    @ApiResponse(responseCode = "200", description = "동기화 성공")
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
    @PostMapping("/sync-popular")
    public ResponseEntity<BulkSyncResult> syncPopularAnime(
            @Parameter(description = "수집할 개수 (기본값: 50, 최대: 5000)")
            @RequestParam(defaultValue = "50") int limit) {

        log.info("인기 애니메이션 일괄 동기화 요청: {}개", limit);

        try {
            // 최대 5000개로 제한 (Jikan API는 수천 개까지 가능)
            int actualLimit = Math.min(limit, 5000);

            if (actualLimit != limit) {
                log.warn("요청된 개수 {}개가 최대 제한을 초과하여 {}개로 조정", limit, actualLimit);
            }

            CollectionResult result = collectorService.collectPopularAnime(actualLimit);

            log.info("인기 애니메이션 동기화 완료: {}", result);
            return ResponseEntity.ok(new BulkSyncResult(true, "인기 애니메이션 동기화 완료", result));

        } catch (Exception e) {
            log.error("인기 애니메이션 동기화 중 오류 발생", e);
            return ResponseEntity.badRequest()
                    .body(new BulkSyncResult(false, "동기화 중 오류 발생: " + e.getMessage(), null));
        }
    }

    // ===== TMDB 보강 =====

    /**
     * 모든 애니메이션 데이터 보완 시작
     */
    @Operation(summary = "전체 애니메이션 데이터 보강", description = "한국어 제목이 없는 애니메이션을 TMDB 데이터로 보강합니다(비동기).")
    @PostMapping("/enhance-all")
    public ResponseEntity<String> enhanceAllAnime() {
        try {
            log.info("애니메이션 데이터 보완 요청 받음");

            // 비동기로 보완 작업 시작 — 즉시 반환하며 진행 상황은 서버 로그로만 확인 가능하다
            animeEnhancementService.enhanceAllAnime();

            return ResponseEntity.ok("애니메이션 데이터 보완 작업이 시작되었습니다. 로그를 확인하세요.");

        } catch (Exception e) {
            log.error("애니메이션 데이터 보완 시작 실패", e);
            return ResponseEntity.status(500).body("보완 작업 시작 실패: " + e.getMessage());
        }
    }

    /**
     * 특정 애니메이션 데이터 보완
     */
    @Operation(summary = "단건 애니메이션 데이터 보강", description = "특정 애니메이션을 TMDB 데이터로 보강합니다.")
    @PostMapping("/enhance/{animeId}")
    public ResponseEntity<String> enhanceAnimeById(@PathVariable Long animeId) {
        try {
            log.info("애니메이션 데이터 보완 요청: ID {}", animeId);

            boolean success = animeEnhancementService.enhanceAnimeById(animeId);

            if (success) {
                return ResponseEntity.ok("애니메이션 데이터 보완이 완료되었습니다. (ID: " + animeId + ")");
            } else {
                return ResponseEntity.status(404).body("애니메이션을 찾을 수 없거나 보완할 데이터가 없습니다. (ID: " + animeId + ")");
            }

        } catch (Exception e) {
            log.error("애니메이션 데이터 보완 실패: ID {}", animeId, e);
            return ResponseEntity.status(500).body("보완 작업 실패: " + e.getMessage());
        }
    }

    /**
     * 보완 작업 상태 확인 (간단한 헬스체크)
     */
    @Operation(summary = "보강 서비스 헬스체크")
    @GetMapping("/enhancement-status")
    public ResponseEntity<String> getEnhancementStatus() {
        return ResponseEntity.ok("애니메이션 데이터 보완 서비스가 정상적으로 작동 중입니다.");
    }

    // ===== 응답 DTO =====

    /**
     * 단일 동기화 결과 DTO
     */
    public static class SyncResult {
        private final boolean success;
        private final String message;
        private final Long malId;

        public SyncResult(boolean success, String message, Long malId) {
            this.success = success;
            this.message = message;
            this.malId = malId;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Long getMalId() { return malId; }
    }

    /**
     * 일괄 동기화 결과 DTO
     */
    public static class BulkSyncResult {
        private final boolean success;
        private final String message;
        private final CollectionResult statistics;

        public BulkSyncResult(boolean success, String message, CollectionResult statistics) {
            this.success = success;
            this.message = message;
            this.statistics = statistics;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public CollectionResult getStatistics() { return statistics; }
    }
}
