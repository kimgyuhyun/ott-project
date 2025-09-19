//package com.ottproject.ottbackend.config;
//
//import com.ottproject.ottbackend.entity.Anime;
//import com.ottproject.ottbackend.repository.AnimeRepository;
//import com.ottproject.ottbackend.service.AnimeBatchProcessor;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.core.annotation.Order;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//
///**
// * 애니메이션 연관 엔티티 데이터 수집 초기화
// *
// * 큰 흐름
// * - 기본 애니메이션 데이터 수집 완료 후 연관 엔티티들을 처리한다. 지금은 사용하지않음
// * - 장르, 스튜디오, 태그 데이터를 수집한다.
// * - 감독(디렉터)는 별도 config에서 처리한다.
// */
//@Component
//@RequiredArgsConstructor
//@Slf4j
//@ConditionalOnProperty(name = "anime.auto-sync.enabled", havingValue = "true", matchIfMissing = true)
//@Order(2) // 두 번째로 실행 (연관 엔티티 데이터 수집)
//public class AnimeAssociationDataInitializer implements CommandLineRunner {
//
//    private final AnimeRepository animeRepository;
//    private final AnimeBatchProcessor animeBatchProcessor;
//
//    @Override
//    public void run(String... args) throws Exception {
//        log.info("🚀 애니메이션 연관 엔티티 데이터 수집 시작");
//
//        try {
//            // 기본 데이터 수집 완료 후 잠시 대기
//            Thread.sleep(3000);
//
//            // 저장된 모든 애니메이션 조회
//            List<Anime> savedAnimes = animeRepository.findAll();
//            log.info("연관 엔티티 처리 대상 애니메이션 수: {}", savedAnimes.size());
//
//            int successCount = 0;
//            int failCount = 0;
//
//            for (Anime anime : savedAnimes) {
//                try {
//                    animeBatchProcessor.processAnimeAssociationsWithoutDirectors(anime.getId());
//                    successCount++;
//                    log.info("✅ 연관 엔티티 처리 완료: {} (ID: {})", anime.getTitle(), anime.getId());
//                } catch (Exception e) {
//                    failCount++;
//                    log.error("❌ 연관 엔티티 처리 실패: {} (ID: {}) - {}", anime.getTitle(), anime.getId(), e.getMessage());
//                }
//            }
//
//            log.info("🎉 애니메이션 연관 엔티티 데이터 수집 완료: 성공 {}개, 실패 {}개", successCount, failCount);
//
//        } catch (Exception e) {
//            log.error("❌ 애니메이션 연관 엔티티 데이터 수집 실패", e);
//        }
//    }
//}
