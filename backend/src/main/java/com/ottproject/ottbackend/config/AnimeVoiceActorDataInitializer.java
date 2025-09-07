//package com.ottproject.ottbackend.config;
//
//import com.ottproject.ottbackend.service.SimpleAnimeDataCollectorService;
//import com.ottproject.ottbackend.repository.AnimeRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.core.annotation.Order;
//import org.springframework.stereotype.Component;
//
///**
// * 애니메이션 성우/캐릭터 데이터 자동 초기화
// *
// * 큰 흐름
// * - TMDB 보완 완료 후 성우/캐릭터 데이터를 수집한다.
// * - 개발 환경에서만 실행되도록 설정한다.
// *
// * 필드 개요
// * - collectorService: 애니메 데이터 수집 서비스
// * - animeRepository: 애니메 데이터 접근
// */
//// @Component
//@RequiredArgsConstructor
//@Slf4j
//// @ConditionalOnProperty(name = "anime.voice-actor.auto-enabled", havingValue = "true", matchIfMissing = false)
//// @Order(3) // 세 번째로 실행 (성우/캐릭터 데이터 수집)
//// public class AnimeVoiceActorDataInitializer implements CommandLineRunner {
//public class AnimeVoiceActorDataInitializer {
//
//    private final SimpleAnimeDataCollectorService collectorService;
//    private final AnimeRepository animeRepository;
//
//    @Override
//    public void run(String... args) throws Exception {
//        log.info("🚀 애니메이션 성우/캐릭터 데이터 자동 초기화 시작");
//
//        try {
//            // TMDB 보완 완료 후 잠시 대기 (DB 트랜잭션 완료 대기)
//            Thread.sleep(10000);
//
//            // 성우/캐릭터 데이터 처리
//            processVoiceActorsAndCharacters();
//
//            log.info("🎉 애니메이션 성우/캐릭터 데이터 자동 초기화 완료");
//
//        } catch (Exception e) {
//            log.error("❌ 애니메이션 성우/캐릭터 데이터 자동 초기화 실패", e);
//        }
//    }
//
//    /**
//     * 성우/캐릭터 데이터 처리
//     * - Order 4에서 실행하여 트랜잭션 충돌 방지
//     */
//    private void processVoiceActorsAndCharacters() {
//        log.info("🎭 성우/캐릭터 데이터 처리 시작");
//
//        try {
//            // 모든 애니메이션 조회
//            var allAnime = animeRepository.findAll();
//            log.info("성우/캐릭터 처리 대상 애니메 수: {}", allAnime.size());
//
//            int successCount = 0;
//            int failCount = 0;
//
//            for (var anime : allAnime) {
//                try {
//                    // 성우/캐릭터 처리 (비동기)
//                    collectorService.processVoiceActorsAndCharactersAsync(anime.getId(), anime.getMalId());
//                    successCount++;
//
//                    // API 제한 고려 (2초 대기)
//                    Thread.sleep(2000);
//
//                } catch (Exception e) {
//                    log.error("성우/캐릭터 처리 실패: ID {} - {}", anime.getId(), e.getMessage());
//                    failCount++;
//                }
//            }
//
//            log.info("🎉 성우/캐릭터 데이터 처리 완료: 성공 {}개, 실패 {}개", successCount, failCount);
//
//        } catch (Exception e) {
//            log.error("❌ 성우/캐릭터 데이터 처리 중 오류 발생", e);
//        }
//    }
//}