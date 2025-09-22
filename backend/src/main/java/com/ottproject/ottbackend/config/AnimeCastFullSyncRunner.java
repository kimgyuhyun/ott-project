//package com.ottproject.ottbackend.config;
//
//import com.ottproject.ottbackend.dto.jikan.AnimeCharactersJikanDto;
//import com.ottproject.ottbackend.entity.Character;
//import com.ottproject.ottbackend.entity.VoiceActor;
//import com.ottproject.ottbackend.repository.AnimeRepository;
//import com.ottproject.ottbackend.repository.CharacterRepository;
//import com.ottproject.ottbackend.repository.VoiceActorRepository;
//import com.ottproject.ottbackend.service.SimpleAnimeDataCollectorService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.core.annotation.Order;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//import java.util.Objects;
//
///**
// * 한 방 풀 동기화 러너
// * - 애니 malId 기준으로 캐릭터/성우 마스터 upsert 후 조인 3종을 모두 채운다
// */
//@Component
//@RequiredArgsConstructor
//@Slf4j
//@ConditionalOnProperty(name = "anime.cast.full-sync.enabled", havingValue = "true", matchIfMissing = false)
//@Order(60)
//public class AnimeCastFullSyncRunner implements CommandLineRunner {
//
//    private final SimpleAnimeDataCollectorService collectorService;
//    private final AnimeRepository animeRepository;
//    private final CharacterRepository characterRepository;
//    private final VoiceActorRepository voiceActorRepository;
//    private final JdbcTemplate jdbcTemplate;
//
//    @Override
//    @Transactional
//    public void run(String... args) throws Exception {
//        log.info("🚀 풀 동기화 시작(malId→캐릭터/성우/조인)");
//
//        var all = animeRepository.findAll();
//        int ok = 0, skip = 0;
//
//        for (var anime : all) {
//            try {
//                Long malId = anime.getMalId();
//                if (malId == null) { skip++; continue; }
//
//                AnimeCharactersJikanDto dto = collectorService.getAnimeCharactersFromJikan(malId);
//                if (dto == null || dto.getData() == null) { skip++; continue; }
//
//                // 이름 기반 upsert용 조회 맵 준비
//                var map = collectorService.convertCharactersToMap(dto);
//                var existingChars = collectorService.mapToExistingCharacters(map, characterRepository);
//                var existingVAs   = collectorService.mapToExistingVoiceActors(map, voiceActorRepository);
//
//                // 캐릭터/성우 마스터에 없으면 생성(최소정보)
//                @SuppressWarnings("unchecked")
//                List<java.util.Map<String,Object>> rows = (List<java.util.Map<String,Object>>) map.getOrDefault("characters", List.of());
//                for (var row : rows) {
//                    @SuppressWarnings("unchecked")
//                    var cm = (java.util.Map<String,Object>) row.getOrDefault("character", java.util.Map.of());
//                    String cname = trim((String) cm.get("name"));
//                    if (cname == null) continue;
//                    Long malCharacterId = (Long) cm.get("mal_id");
//                        Character c = existingChars.stream().filter(x -> cname.equals(trim(x.getName()))).findFirst()
//                                .orElseGet(() -> {
//                                    Character newChar = Character.createCharacter(cname, null, null, null, null);
//                                    Character saved = characterRepository.save(newChar);
//                                    log.info("새 캐릭터 저장: {}", saved.getName());
//                                    return saved;
//                                });
//
//                    // 애니-캐릭터 조인
//                    insertAnimeCharacterIfNotExists(anime.getId(), c.getId());
//
//                    @SuppressWarnings("unchecked")
//                    var vaList = (List<java.util.Map<String,Object>>) row.getOrDefault("voice_actors", List.of());
//                    for (var va : vaList) {
//                        @SuppressWarnings("unchecked")
//                        var person = (java.util.Map<String,Object>) va.getOrDefault("person", java.util.Map.of());
//                        String vname = trim((String) person.get("name"));
//                        if (vname == null) continue;
//                        Long malPersonId = (Long) person.get("mal_id");
//                        VoiceActor v = existingVAs.stream().filter(x -> vname.equals(trim(x.getName()))).findFirst()
//                                .orElseGet(() -> {
//                                    VoiceActor newVA = VoiceActor.createVoiceActor(vname, null, null, null, null);
//                                    VoiceActor saved = voiceActorRepository.save(newVA);
//                                    log.info("새 성우 저장: {}", saved.getName());
//                                    return saved;
//                                });
//
//                        // 캐릭터-성우 조인
//                        insertCharacterVoiceIfNotExists(c.getId(), v.getId());
//                    }
//                }
//
//                // 해당 애니의 애니-성우 조인 생성
//                insertAnimeVoiceForAnime(anime.getId());
//                ok++;
//                Thread.sleep(150); // rate limit 보호
//            } catch (Exception e) {
//                log.warn("풀 동기화 실패 aniId={}", anime.getId(), e);
//            }
//        }
//
//        log.info("🎉 풀 동기화 완료: 성공={}, 스킵={}", ok, skip);
//    }
//
//    private void insertAnimeCharacterIfNotExists(Long animeId, Long characterId) {
//        Boolean exists = jdbcTemplate.queryForObject(
//                "SELECT EXISTS(SELECT 1 FROM anime_characters WHERE anime_id=? AND character_id=?)",
//                Boolean.class, animeId, characterId);
//        if (!Boolean.TRUE.equals(exists)) {
//            jdbcTemplate.update("INSERT INTO anime_characters(anime_id, character_id) VALUES (?,?)", animeId, characterId);
//        }
//    }
//
//    private void insertCharacterVoiceIfNotExists(Long characterId, Long voiceActorId) {
//        Boolean exists = jdbcTemplate.queryForObject(
//                "SELECT EXISTS(SELECT 1 FROM character_voice_actors WHERE character_id=? AND voice_actor_id=?)",
//                Boolean.class, characterId, voiceActorId);
//        if (!Boolean.TRUE.equals(exists)) {
//            jdbcTemplate.update("INSERT INTO character_voice_actors(character_id, voice_actor_id) VALUES (?,?)", characterId, voiceActorId);
//        }
//    }
//
//    private void insertAnimeVoiceForAnime(Long animeId) {
//        jdbcTemplate.update(
//                "INSERT INTO anime_voice_actors(anime_id, voice_actor_id) " +
//                "SELECT DISTINCT ? AS anime_id, cva.voice_actor_id " +
//                "FROM anime_characters ac JOIN character_voice_actors cva ON cva.character_id=ac.character_id " +
//                "LEFT JOIN anime_voice_actors ava ON ava.anime_id=? AND ava.voice_actor_id=cva.voice_actor_id " +
//                "WHERE ac.anime_id=? AND ava.anime_id IS NULL",
//                animeId, animeId, animeId
//        );
//    }
//
//    private static String trim(String s) { return s == null ? null : s.trim(); }
//}
//
//
