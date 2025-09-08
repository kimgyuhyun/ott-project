    //package com.ottproject.ottbackend.config;
    //
    //import com.ottproject.ottbackend.entity.Anime;
    //import com.ottproject.ottbackend.entity.Character;
    //import com.ottproject.ottbackend.entity.VoiceActor;
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
    //
    //import java.util.HashSet;
    //import java.util.List;
    //import java.util.Set;
    //import java.util.stream.Collectors;
    //
    ///**
    // * 조인 전용 초기화 러너
    // * - 캐릭터/성우 마스터가 채워진 상태에서 마지막에 중간 테이블만 채운다
    // * - character_voice_actors, anime_voice_actors upsert
    // */
    //@Component
    //@RequiredArgsConstructor
    //@Slf4j
    //@ConditionalOnProperty(name = "anime.join-mapping.auto-enabled", havingValue = "true", matchIfMissing = false)
    //@Order(6) // 캐릭터(1) → 성우(2) → 조인(3)
    //public class AnimeJoinMappingInitializer implements CommandLineRunner {
    //
    //    private final SimpleAnimeDataCollectorService collectorService;
    //    private final AnimeRepository animeRepository;
    //    private final CharacterRepository characterRepository;
    //    private final VoiceActorRepository voiceActorRepository;
    //
    //    @Override
    //    public void run(String... args) throws Exception {
    //        log.info("🚀 조인 매핑(캐릭터-성우, 애니-성우) 생성 시작");
    //
    //        List<Anime> allAnime = animeRepository.findAll();
    //        log.info("조인 매핑 대상 애니 수: {}", allAnime.size());
    //
    //        int processed = 0;
    //        for (Anime anime : allAnime) {
    //            try {
    //                Long malId = anime.getMalId();
    //                if (malId == null) {
    //                    continue;
    //                }
    //
    //                // Jikan에서 캐릭터/성우 원본 조회 (마스터는 건드리지 않고 조인만 upsert)
    //                AnimeCharactersJikanDto dto = collectorService.getAnimeCharactersFromJikan(malId);
    //                if (dto == null || dto.getData() == null) {
    //                    continue;
    //                }
    //
    //                // 이름 기준으로 기존 마스터 엔티티 조회 후 ID 매핑
    //                var charactersData = collectorService.convertCharactersToMap(dto);
    //                Set<Character> characters = collectorService.mapToExistingCharacters(charactersData, characterRepository);
    //                Set<VoiceActor> voiceActors = collectorService.mapToExistingVoiceActors(charactersData, voiceActorRepository);
    //
    //                // 캐릭터-성우 조인 upsert
    //                collectorService.upsertCharacterVoiceActorJoins(charactersData, characterRepository, voiceActorRepository);
    //
    //                // 애니-성우 = (애니의 캐릭터)×(캐릭터-성우)를 집계해 upsert
    //                Set<Long> voiceActorIds = new HashSet<>();
    //                for (Character character : characters) {
    //                    if (character.getVoiceActors() != null) {
    //                        voiceActorIds.addAll(
    //                            character.getVoiceActors().stream().map(VoiceActor::getId).collect(Collectors.toSet())
    //                        );
    //                    }
    //                }
    //                if (!voiceActorIds.isEmpty()) {
    //                    collectorService.upsertAnimeVoiceActorJoins(anime.getId(), voiceActorIds);
    //                }
    //
    //                processed++;
    //                // 레이트리밋 보호
    //                Thread.sleep(300);
    //            } catch (Exception e) {
    //                log.warn("조인 매핑 실패: aniId={}", anime.getId(), e);
    //            }
    //        }
    //
    //        log.info("🎉 조인 매핑 생성 완료: {}건", processed);
    //    }
    //}
    //
    //
