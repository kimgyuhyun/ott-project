//package com.ottproject.ottbackend.config;
//
//import com.ottproject.ottbackend.dto.jikan.AnimeCharactersJikanDto;
//import com.ottproject.ottbackend.entity.VoiceActor;
//import com.ottproject.ottbackend.repository.AnimeRepository;
//import com.ottproject.ottbackend.repository.VoiceActorRepository;
//import com.ottproject.ottbackend.service.SimpleAnimeDataCollectorService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.core.annotation.Order;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.Set;
//import java.util.stream.Collectors;
//
///**
// * 애니-성우 조인 전용 러너
// * - anime_voice_actors upsert만 수행
// * - LAZY 컬렉션 접근 없이 성우 ID 집계로 처리
// */
//@Component
//@RequiredArgsConstructor
//@Slf4j
//@ConditionalOnProperty(name = "anime.join.anime-voice.enabled", havingValue = "true", matchIfMissing = false)
//@Order(62)
//public class AnimeVoiceJoinRunner implements CommandLineRunner {
//
//    private final SimpleAnimeDataCollectorService collectorService;
//    private final AnimeRepository animeRepository;
//    private final VoiceActorRepository voiceActorRepository;
//
//    @Override
//    @Transactional
//    public void run(String... args) throws Exception {
//        log.info("🚀 애니-성우 조인 upsert 시작");
//
//        var allAnime = animeRepository.findAll();
//        int processed = 0;
//
//        for (var anime : allAnime) {
//            try {
//                Long malId = anime.getMalId();
//                if (malId == null) continue;
//
//                AnimeCharactersJikanDto dto = collectorService.getAnimeCharactersFromJikan(malId);
//                if (dto == null || dto.getData() == null) continue;
//
//                var charactersData = collectorService.convertCharactersToMap(dto);
//
//                // 이미 저장된 성우 마스터에서 이름을 기준으로 조회
//                var existingVoices = collectorService.mapToExistingVoiceActors(charactersData, voiceActorRepository);
//                if (existingVoices == null || existingVoices.isEmpty()) continue;
//
//                Set<Long> voiceActorIds = existingVoices.stream()
//                        .map(VoiceActor::getId)
//                        .filter(java.util.Objects::nonNull)
//                        .collect(Collectors.toSet());
//                if (voiceActorIds.isEmpty()) continue;
//
//                collectorService.upsertAnimeVoiceActorJoins(anime.getId(), voiceActorIds);
//
//                processed++;
//                Thread.sleep(200);
//            } catch (Exception e) {
//                log.warn("애니-성우 조인 upsert 실패: aniId={}", anime.getId(), e);
//            }
//        }
//
//        log.info("🎉 애니-성우 조인 upsert 완료: {}건", processed);
//    }
//}
//
//
