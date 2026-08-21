// package com.ottproject.ottbackend.config;
//
// import com.ottproject.ottbackend.service.SimpleAnimeDataCollectorService;
// import com.ottproject.ottbackend.repository.AnimeRepository;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.boot.CommandLineRunner;
// import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
// import org.springframework.core.annotation.Order;
// import org.springframework.stereotype.Component;
//
/// **
// * 애니메이션 디렉터 데이터 자동 초기화
// *
// * 큰 흐름
// * - TMDB 보완 완료 후 디렉터 데이터를 수집한다.
// * - 개발 환경에서만 실행되도록 설정한다.
// * - 현재 Jikan API에 디렉터 정보가 없어 로그만 출력한다.
// *
// * 필드 개요
// * - collectorService: 애니메 데이터 수집 서비스
// * - animeRepository: 애니메 데이터 접근
// */
// @Component
// @RequiredArgsConstructor
// @Slf4j
// @ConditionalOnProperty(name = "anime.director.auto-enabled", havingValue = "true", matchIfMissing = false)
// @Order(3) // 세 번째로 실행 (디렉터 데이터 수집)
// public class AnimeDirectorDataInitializer implements CommandLineRunner {
//
//    private final SimpleAnimeDataCollectorService collectorService;
//    private final AnimeRepository animeRepository;
//
//    @Override
//    public void run(String... args) throws Exception {
//        log.info("🚀 애니메이션 디렉터 데이터 자동 초기화 시작");
//
//        try {
//            // TMDB 보완 완료 후 잠시 대기 (DB 트랜잭션 완료 대기)
//            Thread.sleep(12000);
//
//            // 디렉터 데이터 처리
//            processDirectors();
//
//            log.info("🎉 애니메이션 디렉터 데이터 자동 초기화 완료");
//
//        } catch (Exception e) {
//            log.error("❌ 애니메이션 디렉터 데이터 자동 초기화 실패", e);
//        }
//    }
//
//    /**
//     * 디렉터 데이터 처리
//     * - Order 3에서 실행하여 트랜잭션 충돌 방지
//     * - 현재 Jikan API에 디렉터 정보가 없어 로그만 출력
//     */
//    private void processDirectors() {
//        log.info("🎬 디렉터 데이터 처리 시작");
//
//        try {
//            // 모든 애니메이션 조회
//            var allAnime = animeRepository.findAll();
//            log.info("디렉터 처리 대상 애니메 수: {}", allAnime.size());
//
//            int successCount = 0;
//            int failCount = 0;
//            int skipCount = 0;
//
//            for (var anime : allAnime) {
//                try {
//                    // MAL ID null 체크 추가
//                    if (anime.getMalId() == null) {
//                        log.warn("MAL ID가 null인 애니메이션 스킵: ID={}, 제목={}", anime.getId(), anime.getTitle());
//                        skipCount++;
//                        continue;
//                    }
//
//                    // 디렉터 처리 (비동기)
//                    collectorService.processDirectorsAsync(anime.getId(), anime.getMalId());
//                    successCount++;
//
//                    // API 제한 고려 (2초 대기)
//                    Thread.sleep(2000);
//
//                } catch (Exception e) {
//                    log.error("디렉터 처리 실패: ID {} - {}", anime.getId(), e.getMessage());
//                    failCount++;
//                }
//            }
//
//            log.info("🎉 디렉터 데이터 처리 완료: 성공 {}개, 실패 {}개, 스킵 {}개", successCount, failCount, skipCount);
//
//        } catch (Exception e) {
//            log.error("❌ 디렉터 데이터 처리 중 오류 발생", e);
//        }
//    }
// }
