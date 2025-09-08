//package com.ottproject.ottbackend.config;
//
//import com.ottproject.ottbackend.dto.jikan.AnimeCharactersJikanDto;
//import com.ottproject.ottbackend.repository.AnimeRepository;
//import com.ottproject.ottbackend.repository.CharacterRepository;
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
///**
// * 캐릭터-성우 조인 전용 러너
// * - character_voice_actors upsert만 수행
// */
//@Component
//@RequiredArgsConstructor
//@Slf4j
//@ConditionalOnProperty(name = "anime.join.character-voice.enabled", havingValue = "true", matchIfMissing = false)
//@Order(61)
//public class CharacterVoiceJoinRunner implements CommandLineRunner {
//
//    private final SimpleAnimeDataCollectorService collectorService;
//    private final AnimeRepository animeRepository;
//    private final CharacterRepository characterRepository;
//    private final VoiceActorRepository voiceActorRepository;
//
//    @Override
//    @Transactional
//    public void run(String... args) throws Exception {
//        log.info("🚀 캐릭터-성우 조인 upsert 시작");
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
//                // 캐릭터-성우 조인 upsert (마스터는 사전 존재 가정)
//                collectorService.upsertCharacterVoiceActorJoins(charactersData, characterRepository, voiceActorRepository);
//
//                processed++;
//                Thread.sleep(200); // 레이트리밋 보호
//            } catch (Exception e) {
//                log.warn("캐릭터-성우 조인 upsert 실패: aniId={}", anime.getId(), e);
//            }
//        }
//
//        log.info("🎉 캐릭터-성우 조인 upsert 완료: {}건", processed);
//    }
//}
//
//
