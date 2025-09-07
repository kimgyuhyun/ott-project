package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.service.AnimeEnhancementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 애니메이션 데이터 보완 컨트롤러
 * 
 * 큰 흐름
 * - Jikan API로 수집된 기본 데이터를 TMDB API로 보완하는 API를 제공한다.
 * - 관리자용 API로 인증이 필요할 수 있다.
 * 
 * 필드 개요
 * - animeEnhancementService: 애니메 데이터 보완 서비스
 */
@RestController
@RequestMapping("/api/admin/anime")
@RequiredArgsConstructor
@Slf4j
public class AnimeEnhancementController {
    
    private final AnimeEnhancementService animeEnhancementService;
    
    /**
     * 모든 애니메이션 데이터 보완 시작
     * 
     * @return 보완 작업 시작 응답
     */
    @PostMapping("/enhance-all")
    public ResponseEntity<String> enhanceAllAnime() {
        try {
            log.info("🚀 애니메이션 데이터 보완 요청 받음");
            
            // 비동기로 보완 작업 시작
            animeEnhancementService.enhanceAllAnime();
            
            return ResponseEntity.ok("애니메이션 데이터 보완 작업이 시작되었습니다. 로그를 확인하세요.");
            
        } catch (Exception e) {
            log.error("애니메이션 데이터 보완 시작 실패", e);
            return ResponseEntity.status(500).body("보완 작업 시작 실패: " + e.getMessage());
        }
    }
    
    /**
     * 특정 애니메이션 데이터 보완
     * 
     * @param animeId 보완할 애니메이션 ID
     * @return 보완 결과
     */
    @PostMapping("/enhance/{animeId}")
    public ResponseEntity<String> enhanceAnimeById(@PathVariable Long animeId) {
        try {
            log.info("🎬 애니메이션 데이터 보완 요청: ID {}", animeId);
            
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
     * 
     * @return 서비스 상태
     */
    @GetMapping("/enhancement-status")
    public ResponseEntity<String> getEnhancementStatus() {
        return ResponseEntity.ok("애니메이션 데이터 보완 서비스가 정상적으로 작동 중입니다.");
    }
}
