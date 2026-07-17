package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.repository.AnimeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 애니메이션 데이터 보완 서비스
 * 
 * 큰 흐름
 * - Jikan API로 수집된 기본 데이터를 TMDB API로 보완한다.
 * - 한국어 제목, 시놉시스, 배경이미지를 추가로 수집한다.
 * 
 * 필드 개요
 * - animeRepository: 애니메 데이터 접근
 * - tmdbApiService: TMDB API 호출
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnimeEnhancementService {
    
    private final AnimeRepository animeRepository;
    private final TmdbApiService tmdbApiService;
    
    /**
     * 모든 애니메이션 데이터 보완 (비동기)
     */
    @Async
    public void enhanceAllAnime() {
        log.info("🚀 애니메이션 데이터 보완 시작");
        
        try {
            // 한국어 정보가 없는 애니메들 조회 (운영자가 큐레이션한 작품은 제외)
            List<Anime> animeWithoutKorean = animeRepository.findByTitleIsNullAndCuratedIsFalse();
            log.info("보완 대상 애니메 수: {}", animeWithoutKorean.size());
            
            int successCount = 0;
            int failCount = 0;
            
            for (Anime anime : animeWithoutKorean) {
                try {
                    boolean enhanced = enhanceSingleAnime(anime);
                    if (enhanced) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                    
                    // API 제한 고려 (1초 대기)
                    Thread.sleep(1000);
                    
                } catch (Exception e) {
                    log.error("애니메 보완 실패: ID {} - {}", anime.getId(), e.getMessage());
                    failCount++;
                }
            }
            
            log.info("🎉 애니메이션 데이터 보완 완료: 성공 {}개, 실패 {}개", successCount, failCount);
            
        } catch (Exception e) {
            log.error("❌ 애니메이션 데이터 보완 중 오류 발생", e);
        }
    }
    
    /**
     * 단일 애니메이션 데이터 보완
     * 
     * @param anime 보완할 애니메이션
     * @return 보완 성공 여부
     */
    @Transactional
    public boolean enhanceSingleAnime(Anime anime) {
        if (anime == null) {
            log.warn("애니메이션 정보가 null");
            return false;
        }
        
        // 검색할 제목 결정 (영어 제목 우선, 없으면 일본어 제목)
        String searchTitle = anime.getTitleEn();
        if (searchTitle == null || searchTitle.trim().isEmpty()) {
            searchTitle = anime.getTitleJp();
        }
        if (searchTitle == null || searchTitle.trim().isEmpty()) {
            log.warn("검색할 제목이 없음: ID {}", anime.getId());
            return false;
        }
        
        log.info("TMDB 검색 시작: {} (ID: {})", searchTitle, anime.getId());
        
        try {
            // TMDB API에서 한국어 정보 조회
            TmdbApiService.TmdbAnimeData tmdbData = tmdbApiService.searchAnime(searchTitle);
            
            if (tmdbData == null) {
                log.warn("TMDB 검색 결과 없음: {}", searchTitle);
                return false;
            }
            
            if (!tmdbData.isHasKoreanData()) {
                log.warn("TMDB 한국어 데이터 없음: {}", searchTitle);
                return false;
            }
            
            // 한국어 정보 업데이트
            boolean updated = false;
            
            // 한국어 제목 업데이트
            if (anime.getTitle() == null && tmdbData.getTitle() != null) {
                anime.setTitle(tmdbData.getTitle());
                updated = true;
                log.info("한국어 제목 업데이트: {} → {}", searchTitle, tmdbData.getTitle());
            }
            
            // 한국어 시놉시스 업데이트
            if ((anime.getSynopsis() == null || anime.getSynopsis().isEmpty()) && 
                tmdbData.getOverview() != null) {
                anime.setSynopsis(tmdbData.getOverview());
                anime.setFullSynopsis(tmdbData.getOverview());
                updated = true;
                log.info("한국어 시놉시스 업데이트: ID {}", anime.getId());
            }
            
            // 배경 이미지 업데이트
            if (anime.getBackdropUrl() == null && tmdbData.getBackdropUrl() != null) {
                anime.setBackdropUrl(tmdbData.getBackdropUrl());
                updated = true;
                log.info("배경 이미지 업데이트: ID {}", anime.getId());
            }
            
            // 포스터 이미지 업데이트 (Jikan API 포스터가 없거나 품질이 낮은 경우)
            if ((anime.getPosterUrl() == null || anime.getPosterUrl().isEmpty()) && 
                tmdbData.getPosterUrl() != null) {
                anime.setPosterUrl(tmdbData.getPosterUrl());
                updated = true;
                log.info("포스터 이미지 업데이트: ID {}", anime.getId());
            }
            
            if (updated) {
                animeRepository.save(anime);
                log.info("✅ 애니메 보완 완료: ID {} - {}", anime.getId(), tmdbData.getTitle());
                return true;
            } else {
                log.info("ℹ️ 업데이트할 정보 없음: ID {}", anime.getId());
                return false;
            }
            
        } catch (Exception e) {
            log.error("TMDB API 호출 실패: {} - {}", searchTitle, e.getMessage());
            return false;
        }
    }
    
    /**
     * 특정 애니메이션 ID로 보완
     * 
     * @param animeId 보완할 애니메이션 ID
     * @return 보완 성공 여부
     */
    @Transactional
    public boolean enhanceAnimeById(Long animeId) {
        if (animeId == null) {
            log.warn("애니메이션 ID가 null");
            return false;
        }
        
        Anime anime = animeRepository.findById(animeId).orElse(null);
        if (anime == null) {
            log.warn("애니메이션을 찾을 수 없음: ID {}", animeId);
            return false;
        }
        
        return enhanceSingleAnime(anime);
    }
}
