//package com.ottproject.ottbackend.config;
//
//import com.ottproject.ottbackend.service.AnimeEnhancementService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.core.annotation.Order;
//import org.springframework.stereotype.Component;
//
///**
// * 애니메이션 데이터 보완 자동 초기화
// *
// * 큰 흐름
// * - Jikan API 데이터 수집 완료 후 TMDB API로 한국어 정보를 보완한다.
// * - 개발 환경에서만 실행되도록 설정한다.
// *
// * 필드 개요
// * - animeEnhancementService: 애니메 데이터 보완 서비스
// */
//// @Component
//@RequiredArgsConstructor
//@Slf4j
//// @ConditionalOnProperty(name = "anime.enhancement.auto-enabled", havingValue = "true", matchIfMissing = false)
//// @Order(7) // (TMDB 한국어 정보 보완)
//// public class AnimeEnhancementInitializer implements CommandLineRunner {
//public class AnimeEnhancementInitializer {
//
//    private final AnimeEnhancementService animeEnhancementService;
//
//    @Override
//    public void run(String... args) throws Exception {
//        log.info("🚀 애니메이션 데이터 보완 자동 초기화 시작");
//
//        try {
//            // Jikan API 수집 완료 후 잠시 대기 (DB 트랜잭션 완료 대기)
//            Thread.sleep(5000);
//
//            // TMDB API로 한국어 정보 보완 시작
//            animeEnhancementService.enhanceAllAnime();
//
//            log.info("🎉 애니메이션 데이터 보완 자동 초기화 완료");
//
//        } catch (Exception e) {
//            log.error("❌ 애니메이션 데이터 보완 자동 초기화 실패", e);
//        }
//    }
//}
