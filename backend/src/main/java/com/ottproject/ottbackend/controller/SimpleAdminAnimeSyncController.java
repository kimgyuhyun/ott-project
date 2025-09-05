package com.ottproject.ottbackend.controller;

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
 * 간단한 관리자 애니메이션 동기화 컨트롤러 (DTO 없이)
 * 
 * 큰 흐름
 * - Jikan API에서 애니메이션 데이터를 수집하여 DB에 저장하는 관리자용 API를 제공한다.
 * - 19금 콘텐츠는 자동으로 필터링된다.
 */
@RestController
@RequestMapping("/api/admin/anime")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "관리자 애니메이션 동기화", description = "Jikan API를 통한 애니메이션 데이터 수집 API")
public class SimpleAdminAnimeSyncController {
    
    private final SimpleAnimeDataCollectorService collectorService;
    
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
        
        log.info("🚀 인기 애니메이션 일괄 동기화 요청: {}개", limit);
        
        try {
            // 최대 5000개로 제한 (Jikan API는 수천 개까지 가능)
            int actualLimit = Math.min(limit, 5000);
            
            if (actualLimit != limit) {
                log.warn("⚠️ 요청된 개수 {}개가 최대 제한을 초과하여 {}개로 조정", limit, actualLimit);
            }
            
            CollectionResult result = collectorService.collectPopularAnime(actualLimit);
            
            log.info("🎉 인기 애니메이션 동기화 완료: {}", result);
            return ResponseEntity.ok(new BulkSyncResult(true, "인기 애니메이션 동기화 완료", result));
            
        } catch (Exception e) {
            log.error("❌ 인기 애니메이션 동기화 중 오류 발생", e);
            return ResponseEntity.badRequest()
                .body(new BulkSyncResult(false, "동기화 중 오류 발생: " + e.getMessage(), null));
        }
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
