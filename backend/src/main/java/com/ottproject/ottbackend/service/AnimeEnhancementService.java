package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.repository.AnimeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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

    // 자기 자신을 프록시로 얻기 위한 provider.
    // 같은 클래스 안에서 enhanceAnimeById(...) 를 직접 호출하면 프록시를 타지 않아 @Transactional 이 적용되지 않는다.
    // (ObjectProvider 는 지연 조회라 자기 참조로 인한 순환 의존성이 생기지 않는다 —
    //  SimpleAnimeDataCollectorService 가 같은 이유로 쓰는 패턴)
    private final ObjectProvider<AnimeEnhancementService> selfProvider;

    /**
     * 모든 애니메이션 데이터 보완 (비동기)
     *
     * 엔티티가 아니라 ID 목록만 들고 도는 이유(중요)
     * - 이 메서드는 @Async 이고 트랜잭션이 없다. 여기서 엔티티를 조회하면 조회 트랜잭션이 즉시 끝나면서
     *   전부 준영속(detached) 상태가 된다.
     * - 항목마다 1초씩 쉬므로 수천 건이면 배치가 몇 시간 돈다. 그 사이 준영속 엔티티는 시작 시점의
     *   낡은 스냅샷을 그대로 들고 있다.
     * - 준영속 엔티티를 save() 하면 merge 가 되고, merge 는 DB 의 현재 값 위에 스냅샷의 '모든 필드'를
     *   덮어쓴다. 그래서 배치가 도는 동안 운영자가 한 수정(배지/제목/활성 여부)이 조용히 되돌아갔다.
     * - ID 만 들고 다니며 항목마다 트랜잭션 안에서 다시 조회하면, 언제나 방금 읽은 최신 상태를 수정한다.
     */
    @Async
    public void enhanceAllAnime() {
        log.info("애니메이션 데이터 보완 시작");

        try {
            // 한국어 정보가 없는 애니메들의 ID 만 조회 (운영자가 큐레이션한 작품은 제외)
            List<Long> targetIds = animeRepository.findByTitleIsNullAndCuratedIsFalse()
                    .stream()
                    .map(Anime::getId)
                    .toList();
            log.info("보완 대상 애니메 수: {}", targetIds.size());

            int successCount = 0;
            int failCount = 0;

            for (Long animeId : targetIds) {
                try {
                    // 프록시 경유 호출: 항목마다 enhanceAnimeById 의 @Transactional 이 독립적으로 적용된다.
                    // (직접 호출하면 self-invocation 이라 트랜잭션 없이 준영속 엔티티를 merge 하게 된다)
                    boolean enhanced = selfProvider.getObject().enhanceAnimeById(animeId);
                    if (enhanced) {
                        successCount++;
                    } else {
                        failCount++;
                    }

                    // API 제한 고려 (1초 대기)
                    Thread.sleep(1000);

                } catch (Exception e) {
                    log.error("애니메 보완 실패: ID {} - {}", animeId, e.getMessage());
                    failCount++;
                }
            }

            log.info("애니메이션 데이터 보완 완료: 성공 {}개, 실패 {}개", successCount, failCount);

        } catch (Exception e) {
            log.error("애니메이션 데이터 보완 중 오류 발생", e);
        }
    }
    
    /**
     * 단일 애니메이션 데이터 보완
     *
     * 반드시 호출자(enhanceAnimeById)의 트랜잭션 안에서, 영속 상태인 엔티티로 호출해야 한다.
     * 여기에 @Transactional 을 붙이지 않는 것은 의도적이다 — private 메서드는 프록시가 가로채지 못해
     * 애너테이션이 아무 일도 하지 않으면서 트랜잭션이 걸린 것처럼 보이게 만든다.
     *
     * @param anime 보완할 애니메이션(영속 상태)
     * @return 보완 성공 여부
     */
    private boolean enhanceSingleAnime(Anime anime) {
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
                // save() 를 호출하지 않는다: 엔티티가 영속 상태라 더티 체킹으로 커밋 시점에 반영된다.
                // (준영속 엔티티에 save() 를 쓰면 merge 가 되어 낡은 스냅샷 전체를 덮어쓰므로 그 경로를 없앴다)
                log.info("애니메 보완 완료: ID {} - {}", anime.getId(), tmdbData.getTitle());
                return true;
            } else {
                log.info("업데이트할 정보 없음: ID {}", anime.getId());
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
     * 보완의 유일한 진입점이다. 항상 트랜잭션 안에서 ID 로 다시 조회하므로 엔티티가 영속 상태이고,
     * 수정은 더티 체킹으로 반영된다(준영속 merge 로 인한 낡은 값 덮어쓰기가 원천적으로 불가능).
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

        // 대상 목록을 만든 뒤 운영자가 큐레이션했을 수 있다(배치는 몇 시간 돈다).
        // 방금 읽은 최신 상태로 다시 확인해야 사람의 판단을 덮어쓰지 않는다.
        if (Boolean.TRUE.equals(anime.getCurated())) {
            log.info("운영자가 큐레이션한 작품이라 보완을 건너뜀: ID {}", animeId);
            return false;
        }

        return enhanceSingleAnime(anime);
    }
}
