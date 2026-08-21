// package com.ottproject.ottbackend.config;
//
// import com.ottproject.ottbackend.service.SimpleAnimeDataCollectorService;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.boot.CommandLineRunner;
// import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
// import org.springframework.core.annotation.Order;
// import org.springframework.stereotype.Component;
//
/// **
// * 애니메이션 기본 데이터 수집 초기화
// *
// * 큰 흐름
// * - 애플리케이션 시작 시 Jikan API를 호출하여 애니메이션 기본 정보를 수집한다. 장르, 스튜디오 태그도 포함
// * - 개발 환경에서만 실행되도록 설정한다.
// */
// @Component
// @RequiredArgsConstructor
// @Slf4j
// @ConditionalOnProperty(name = "anime.auto-sync.enabled", havingValue = "true", matchIfMissing = true)
// @Order(1) // 첫 번째로 실행 (기본 애니메이션 데이터 수집)
// public class AnimeBasicDataInitializer implements CommandLineRunner {
//    private final SimpleAnimeDataCollectorService collectorService;
//
//    @Override
//    public void run(String... args) throws Exception {
//        log.info("🚀 애니메이션 기본 데이터 수집 시작");
//
//        try {
//            // 초기 수집 개수: 시스템 프로퍼티/환경변수 기반, 기본 500
//            int limit = 500;
//            try {
//                String prop = System.getProperty("anime.init.limit");
//                if (prop == null || prop.isBlank()) {
//                    prop = System.getenv("ANIME_INIT_LIMIT");
//                }
//                if (prop != null && !prop.isBlank()) {
//                    limit = Math.max(1, Integer.parseInt(prop));
//                }
//            } catch (Exception ignore) {}
//
//            var result = collectorService.collectPopularAnime(limit);
//            log.info("🎉 애니메이션 기본 데이터 수집 완료: {}", result);
//
//        } catch (Exception e) {
//            log.error("❌ 애니메이션 기본 데이터 수집 실패", e);
//        }
//    }
// }
