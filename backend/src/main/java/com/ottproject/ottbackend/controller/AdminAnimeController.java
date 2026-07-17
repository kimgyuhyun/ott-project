package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.dto.admin.AdminAnimeDetailDto;
import com.ottproject.ottbackend.dto.admin.AdminAnimeListItemDto;
import com.ottproject.ottbackend.dto.admin.AnimeBulkCurationPreviewResponse;
import com.ottproject.ottbackend.dto.admin.AnimeBulkCurationRequest;
import com.ottproject.ottbackend.dto.admin.AnimeCurationSearchCondition;
import com.ottproject.ottbackend.dto.admin.AnimeCurationUpdateRequest;
import com.ottproject.ottbackend.service.AnimeCurationService;
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
 * - GET /search: 큐레이션 대상 동적 검색
 * - GET /{animeId}: 단건 조회(수정 폼용)
 * - PATCH /{animeId}: 단건 큐레이션 수정
 * - POST /bulk/preview: 벌크 수정 영향 건수 미리보기
 * - PATCH /bulk: 조건 기반 벌크 큐레이션
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
@Tag(name = "관리자 애니메이션 관리", description = "큐레이션 / Jikan 동기화 / TMDB 보강 API")
public class AdminAnimeController {

    private final SimpleAnimeDataCollectorService collectorService;
    private final AnimeEnhancementService animeEnhancementService;
    private final AnimeCurationService animeCurationService;

    // ===== 큐레이션 =====

    /**
     * 큐레이션 대상 검색
     *
     * 조건은 전부 선택이며 자유롭게 조합된다. 아무 조건도 주지 않으면 전체 목록이다.
     * 조건 객체는 쿼리 파라미터로 바인딩된다(예: ?status=ONGOING&year=2026&isActive=false).
     */
    @Operation(summary = "애니 큐레이션 검색",
            description = "제목(한/영/일 통합)/상태/연도/노출여부/배지/큐레이션여부/유입경로를 자유 조합해 검색합니다.")
    @ApiResponse(responseCode = "200", description = "검색 성공")
    @GetMapping("/search")
    public ResponseEntity<PagedResponse<AdminAnimeListItemDto>> searchForCuration(
            AnimeCurationSearchCondition condition,
            @Parameter(description = "페이지 번호(0-base)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(animeCurationService.search(condition, page, size));
    }

    /**
     * 단건 조회 (수정 폼용)
     */
    @Operation(summary = "애니 단건 조회", description = "큐레이션 수정 폼에 채울 현재 값을 조회합니다(줄거리 포함).")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "애니메이션 없음")
    @GetMapping("/{animeId}")
    public ResponseEntity<AdminAnimeDetailDto> getForCuration(@PathVariable Long animeId) {
        return ResponseEntity.ok(animeCurationService.get(animeId));
    }

    /**
     * 단건 큐레이션 수정
     *
     * 부분 수정이다 — 요청에 없는(null) 필드는 그대로 둔다.
     */
    @Operation(summary = "애니 단건 큐레이션 수정",
            description = "제목/줄거리/이미지/배지/노출 여부를 수정합니다. 전달하지 않은 필드는 변경하지 않습니다. "
                    + "콘텐츠(제목/줄거리/이미지)가 실제로 바뀌면 curated 가 켜져 TMDB 자동 보강에서 제외됩니다.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "404", description = "애니메이션 없음")
    @PatchMapping("/{animeId}")
    public ResponseEntity<AdminAnimeDetailDto> updateCuration(
            @PathVariable Long animeId,
            @RequestBody AnimeCurationUpdateRequest request) {

        return ResponseEntity.ok(animeCurationService.update(animeId, request));
    }

    /**
     * 벌크 수정 미리보기
     *
     * 실행 전에 몇 건이 바뀌는지 확인한다. 여기서 받은 affectedCount 를 벌크 요청의 expectedCount 로
     * 되돌려 보내야 실행된다.
     */
    @Operation(summary = "벌크 큐레이션 미리보기",
            description = "조건에 걸린 건수와 표본을 돌려줍니다. 실제 수정은 하지 않습니다.")
    @ApiResponse(responseCode = "200", description = "미리보기 성공")
    @ApiResponse(responseCode = "400", description = "조건이 비어 있음")
    @PostMapping("/bulk/preview")
    public ResponseEntity<AnimeBulkCurationPreviewResponse> previewBulkCuration(
            @RequestBody AnimeCurationSearchCondition condition) {

        return ResponseEntity.ok(animeCurationService.previewBulkCuration(condition));
    }

    /**
     * 조건 기반 벌크 큐레이션
     *
     * 조건에 걸린 작품 전체에 같은 배지/노출 여부를 적용한다(제목/포스터는 대상이 아니다).
     */
    @Operation(summary = "조건 기반 벌크 큐레이션",
            description = "검색 조건에 걸린 작품 전체에 배지/노출 여부를 일괄 적용합니다. "
                    + "미리보기에서 확인한 건수를 expectedCount 로 함께 보내야 합니다.")
    @ApiResponse(responseCode = "200", description = "적용 성공(영향 건수 반환)")
    @ApiResponse(responseCode = "400", description = "조건이 비었거나 변경할 값이 없음")
    @ApiResponse(responseCode = "409", description = "대상 건수가 미리보기와 다름")
    @PatchMapping("/bulk")
    public ResponseEntity<BulkCurationResult> applyBulkCuration(@RequestBody AnimeBulkCurationRequest request) {
        long affected = animeCurationService.applyBulkCuration(request);
        return ResponseEntity.ok(new BulkCurationResult(affected));
    }

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
     * 벌크 큐레이션 결과 DTO
     */
    public static class BulkCurationResult {
        private final long affectedCount;

        public BulkCurationResult(long affectedCount) {
            this.affectedCount = affectedCount;
        }

        public long getAffectedCount() { return affectedCount; }
    }

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
