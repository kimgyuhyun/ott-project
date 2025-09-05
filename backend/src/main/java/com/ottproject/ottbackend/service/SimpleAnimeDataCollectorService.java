package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.entity.Genre;
import com.ottproject.ottbackend.entity.Studio;
import com.ottproject.ottbackend.entity.Tag;
import com.ottproject.ottbackend.entity.Director;
import com.ottproject.ottbackend.entity.VoiceActor;
import com.ottproject.ottbackend.entity.Character;
import com.ottproject.ottbackend.exception.AdultContentException;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.GenreRepository;
import com.ottproject.ottbackend.repository.StudioRepository;
import com.ottproject.ottbackend.repository.TagRepository;
import com.ottproject.ottbackend.repository.DirectorRepository;
import com.ottproject.ottbackend.repository.VoiceActorRepository;
import com.ottproject.ottbackend.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 간단한 애니메이션 데이터 수집 서비스 (DTO 없이)
 * 
 * 큰 흐름
 * - Jikan API에서 애니메이션 데이터를 수집하여 DB에 저장한다.
 * - 19금 콘텐츠는 자동으로 필터링한다.
 * - 장르와 스튜디오는 중복을 방지하여 저장한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimpleAnimeDataCollectorService {
    
    private final SimpleJikanApiService jikanApiService;
    private final SimpleJikanDataMapper dataMapper;
    private final AnimeRepository animeRepository;
    private final GenreRepository genreRepository;
    private final StudioRepository studioRepository;
    private final TagRepository tagRepository;
    private final DirectorRepository directorRepository;
    private final VoiceActorRepository voiceActorRepository;
    private final CharacterRepository characterRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    /**
     * 단일 애니메이션 수집
     * - 외부 API 호출 이후 DB 쓰기 수행. 호출부가 배치 트랜잭션일 수 있어 독립 경계를 유지.
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public boolean collectAnime(Long malId) {
        try {
            log.info("🎬 애니메이션 수집 시작: MAL ID {}", malId);
            
            // 1. Jikan API 호출
            log.info("📡 Jikan API 호출 중...");
            var details = jikanApiService.getAnimeDetails(malId);
            if (details == null) {
                log.warn("❌ 애니메이션 데이터 없음: MAL ID {}", malId);
                return false;
            }
            
            // DTO → Map 어댑트 (현 매퍼 호환용 최소 변환)
            Map<String, Object> jikanData = new java.util.HashMap<>();
            jikanData.put("title", details.getTitle());
            jikanData.put("title_english", details.getTitle_english());
            jikanData.put("title_japanese", details.getTitle_japanese());
            jikanData.put("synopsis", details.getSynopsis());
            jikanData.put("episodes", details.getEpisodes());
            jikanData.put("status", details.getStatus());
            jikanData.put("type", details.getType());
            jikanData.put("source", details.getSource());
            jikanData.put("duration", details.getDuration());
            jikanData.put("score", details.getScore());
            jikanData.put("scored_by", details.getScored_by());
            jikanData.put("rating", details.getRating());
            // aired
            var aired = new java.util.HashMap<String, Object>();
            if (details.getAired() != null) {
                aired.put("from", details.getAired().getFrom());
                aired.put("to", details.getAired().getTo());
            }
            jikanData.put("aired", aired);
            // broadcast
            var broadcast = new java.util.HashMap<String, Object>();
            if (details.getBroadcast() != null) {
                broadcast.put("day", details.getBroadcast().getDay());
                broadcast.put("time", details.getBroadcast().getTime());
            }
            jikanData.put("broadcast", broadcast);
            // images
            var images = new java.util.HashMap<String, Object>();
            if (details.getImages() != null && details.getImages().getJpg() != null) {
                var jpg = new java.util.HashMap<String, Object>();
                jpg.put("image_url", details.getImages().getJpg().getImage_url());
                images.put("jpg", jpg);
            }
            jikanData.put("images", images);
            // arrays
            List<Map<String, Object>> genres = new java.util.ArrayList<>();
            if (details.getGenres() != null) {
                for (var g : details.getGenres()) {
                    genres.add(java.util.Map.of("name", g.getName()));
                }
            }
            jikanData.put("genres", genres);
            List<Map<String, Object>> studios = new java.util.ArrayList<>();
            if (details.getStudios() != null) {
                for (var s : details.getStudios()) {
                    studios.add(java.util.Map.of("name", s.getName()));
                }
            }
            jikanData.put("studios", studios);
            List<Map<String, Object>> themes = new java.util.ArrayList<>();
            if (details.getThemes() != null) {
                for (var t : details.getThemes()) {
                    themes.add(java.util.Map.of("name", t.getName()));
                }
            }
            jikanData.put("themes", themes);
            List<Map<String, Object>> demographics = new java.util.ArrayList<>();
            if (details.getDemographics() != null) {
                for (var d : details.getDemographics()) {
                    demographics.add(java.util.Map.of("name", d.getName()));
                }
            }
            jikanData.put("demographics", demographics);
            // staff (optional)
            if (details.getStaff() != null) {
                List<Map<String, Object>> staff = new java.util.ArrayList<>();
                for (var st : details.getStaff()) {
                    staff.add(new java.util.HashMap<>(java.util.Map.of(
                        "name", st.getName() == null ? "" : st.getName(),
                        "positions", st.getPositions() == null ? java.util.List.of() : st.getPositions()
                    )));
                }
                jikanData.put("staff", staff);
            }
            
            String title = (String) jikanData.get("title");
            log.info("📺 애니메이션 제목: {}", title);
            if (title == null || title.isBlank()) {
                log.warn("❌ 유효하지 않은 제목 데이터: MAL ID {}", malId);
                return false;
            }
            
            // 2. 중복 체크 (읽기 전용, 동일 세션 간 충돌 방지 위해 별도 경계에서 수행하지 않음)
            log.info("🔍 중복 체크 중...");
            if (checkAnimeExists(title)) {
                log.info("⚠️ 이미 존재하는 애니메이션: {}", title);
                return false;
            }
            
            // 3. Anime 엔티티 변환 (19금 체크 포함)
            log.info("🔄 애니메이션 엔티티 변환 중...");
            Anime anime = dataMapper.mapToAnime(jikanData);
            log.info("✅ 애니메이션 엔티티 변환 완료: {}", anime.getTitle());
            
            // 4. 감독 처리
            log.info("🎬 감독 정보 처리 중...");
            Set<Director> directorSet = dataMapper.mapToDirectors(jikanData);
            Set<Director> processedDirectors = processDirectors(directorSet);
            anime.setDirectors(processedDirectors);
            
            // 5. 성우/캐릭터 정보 추가 (별도 API 호출)
            log.info("🎤 성우/캐릭터 정보 수집 중...");
            var charactersDto = jikanApiService.getAnimeCharacters(malId);
            // DTO → Map 어댑트 (현 매퍼 호환용 최소 변환)
            Map<String, Object> charactersData = new java.util.HashMap<>();
            List<Map<String, Object>> charactersList = new java.util.ArrayList<>();
            if (charactersDto != null && charactersDto.getData() != null) {
                for (var item : charactersDto.getData()) {
                    Map<String, Object> one = new java.util.HashMap<>();
                    // character
                    Map<String, Object> character = new java.util.HashMap<>();
                    if (item.getCharacter() != null) {
                        character.put("name", item.getCharacter().getName());
                        Map<String, Object> img = new java.util.HashMap<>();
                        if (item.getCharacter().getImages() != null && item.getCharacter().getImages().getJpg() != null) {
                            Map<String, Object> jpg = new java.util.HashMap<>();
                            jpg.put("image_url", item.getCharacter().getImages().getJpg().getImage_url());
                            img.put("jpg", jpg);
                        }
                        character.put("images", img);
                    }
                    one.put("character", character);
                    // voice_actors
                    List<Map<String, Object>> vaList = new java.util.ArrayList<>();
                    if (item.getVoice_actors() != null) {
                        for (var va : item.getVoice_actors()) {
                            Map<String, Object> vaMap = new java.util.HashMap<>();
                            vaMap.put("language", va.getLanguage());
                            Map<String, Object> person = new java.util.HashMap<>();
                            if (va.getPerson() != null) person.put("name", va.getPerson().getName());
                            vaMap.put("person", person);
                            vaList.add(vaMap);
                        }
                    }
                    one.put("voice_actors", vaList);
                    charactersList.add(one);
                }
            }
            charactersData.put("characters", charactersList);
            Set<VoiceActor> voiceActorSet = dataMapper.mapToVoiceActors(charactersData);
            Set<VoiceActor> processedVoiceActors = processVoiceActors(voiceActorSet);
            anime.setVoiceActors(processedVoiceActors);
            
            Set<Character> characterSet = dataMapper.mapToCharacters(charactersData);
            Set<Character> processedCharacters = processCharacters(characterSet);
            anime.setCharacters(processedCharacters);
            
            // 6. 장르 처리
            log.info("🎭 장르 정보 처리 중...");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> genresList = (List<Map<String, Object>>) jikanData.get("genres");
            Set<Genre> genreSet = processGenres(genresList);
            anime.setGenres(genreSet);
            
            // 7. 스튜디오 처리
            log.info("🏢 스튜디오 정보 처리 중...");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> studiosList = (List<Map<String, Object>>) jikanData.get("studios");
            Set<Studio> studioSet = processStudios(studiosList);
            anime.setStudios(studioSet);
            
            // 8. 태그 처리 (themes + demographics)
            log.info("🏷️ 태그 정보 처리 중...");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> themesList = (List<Map<String, Object>>) jikanData.get("themes");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> demographicsList = (List<Map<String, Object>>) jikanData.get("demographics");
            Set<Tag> tagSet = processTags(themesList, demographicsList);
            anime.setTags(tagSet);
            
            // 9. DB 저장
            log.info("💾 DB 저장 중...");
            animeRepository.save(anime);
            // 메모리 사용량 최적화: 단건 처리 후 즉시 flush/clear
            entityManager.flush();
            entityManager.clear();
            
            log.info("🎉 애니메이션 수집 완료: {} (MAL ID: {})", anime.getTitle(), malId);
            return true;
            
        } catch (AdultContentException e) {
            log.info("🚫 19금 콘텐츠 제외: MAL ID {} - {}", malId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("❌ 애니메이션 수집 실패: MAL ID {}", malId, e);
            return false;
        }
    }
    
    /**
     * 애니메이션 중복 체크 (읽기 전용)
     * - 동일 트랜잭션 내에서만 사용하여 세션 충돌 방지
     */
    @Transactional(readOnly = true)
    public boolean checkAnimeExists(String title) {
        return animeRepository.existsByTitle(title);
    }
    
    /**
     * 인기 애니메이션 일괄 수집
     * - 배치는 읽기 전용 트랜잭션을 사용하지 않고, 각 단건 작업을 REQUIRES_NEW로 분리하여 독립 롤백 보장
     */
    public CollectionResult collectPopularAnime(int limit) {
        log.info("🚀 인기 애니메이션 일괄 수집 시작: {}개", limit);
        
        List<Long> popularIds = jikanApiService.getPopularAnimeIds(limit);
        if (popularIds.isEmpty()) {
            log.warn("❌ 인기 애니메이션 ID 목록이 비어있음");
            return new CollectionResult(0, 0, 0);
        }
        
        log.info("📋 수집할 애니메이션 ID 목록: {}개", popularIds.size());
        
        int successCount = 0;
        int adultContentCount = 0;
        int errorCount = 0;
        
        for (int i = 0; i < popularIds.size(); i++) {
            Long malId = popularIds.get(i);
            log.info("📺 [{}/{}] 애니메이션 수집 시작: MAL ID {}", i + 1, popularIds.size(), malId);
            
            try {
                boolean success = collectAnime(malId);
                if (success) {
                    successCount++;
                    log.info("✅ [{}/{}] 수집 성공: MAL ID {}", i + 1, popularIds.size(), malId);
                } else {
                    log.warn("⚠️ [{}/{}] 수집 실패 (중복 또는 기타 이유): MAL ID {}", i + 1, popularIds.size(), malId);
                }
                
                // Rate limit 대응
                log.debug("⏳ Rate limit 대응: 대기");
                jikanApiService.delayForRateLimit();
                
            } catch (AdultContentException e) {
                adultContentCount++;
                log.info("🚫 [{}/{}] 19금 콘텐츠 제외: MAL ID {} - {}", i + 1, popularIds.size(), malId, e.getMessage());
            } catch (Exception e) {
                errorCount++;
                log.error("❌ [{}/{}] 수집 중 오류: MAL ID {}", i + 1, popularIds.size(), malId, e);
            }
        }
        
        CollectionResult result = new CollectionResult(successCount, adultContentCount, errorCount);
        log.info("🎉 인기 애니메이션 수집 완료: {}", result);
        
        return result;
    }
    
    /**
     * 장르 처리 및 저장
     */
    private Set<Genre> processGenres(List<Map<String, Object>> jikanGenres) {
        Set<Genre> genres = new java.util.HashSet<>();
        
        if (jikanGenres != null) {
            log.info("🎭 장르 처리 시작: {}개", jikanGenres.size());
            for (Map<String, Object> jikanGenre : jikanGenres) {
                String name = (String) jikanGenre.get("name");
                if (name != null) {
                    // 중복 체크
                    Optional<Genre> existingGenre = genreRepository.findByName(name);
                    
                    if (existingGenre.isPresent()) {
                        // 이미 존재하는 장르
                        genres.add(existingGenre.get());
                        log.debug("기존 장르 사용: {}", name);
                    } else {
                        // 새로운 장르 생성
                        Genre genre = Genre.createGenre(name, "", generateConsistentColor(name));
                        
                        Genre savedGenre = genreRepository.save(genre);
                        genres.add(savedGenre);
                        log.info("새 장르 생성: {} (색상: {})", genre.getName(), genre.getColor());
                    }
                }
            }
            log.info("장르 처리 완료: 총 {}개", genres.size());
        }
        
        return genres;
    }
    
    /**
     * 스튜디오 처리 및 저장
     */
    private Set<Studio> processStudios(List<Map<String, Object>> jikanStudios) {
        Set<Studio> studios = new java.util.HashSet<>();
        
        if (jikanStudios != null) {
            log.info("🎬 스튜디오 처리 시작: {}개", jikanStudios.size());
            for (Map<String, Object> jikanStudio : jikanStudios) {
                String name = (String) jikanStudio.get("name");
                if (name != null) {
                    // 중복 체크
                    Optional<Studio> existingStudio = studioRepository.findByName(name);
                    
                    if (existingStudio.isPresent()) {
                        // 이미 존재하는 스튜디오
                        studios.add(existingStudio.get());
                        log.debug("기존 스튜디오 사용: {}", name);
                    } else {
                        // 새로운 스튜디오 생성
                        Studio studio = Studio.createStudio(name, null, null, "", "", "", "일본");
                        
                        Studio savedStudio = studioRepository.save(studio);
                        studios.add(savedStudio);
                        log.info("새 스튜디오 생성: {}", studio.getName());
                    }
                }
            }
            log.info("스튜디오 처리 완료: 총 {}개", studios.size());
        }
        
        return studios;
    }
    
    /**
     * 태그 처리 및 저장 (themes + demographics)
     */
    private Set<Tag> processTags(List<Map<String, Object>> themes, List<Map<String, Object>> demographics) {
        Set<Tag> tags = new java.util.HashSet<>();
        
        // themes 처리
        if (themes != null) {
            log.info("테마 태그 처리 시작: {}개", themes.size());
            for (Map<String, Object> theme : themes) {
                String name = (String) theme.get("name");
                if (name != null) {
                    // 기존 태그 찾기
                    Optional<Tag> existingTag = tagRepository.findByName(name);
                    if (existingTag.isPresent()) {
                        tags.add(existingTag.get());
                        log.debug("기존 테마 태그 사용: {}", name);
                    } else {
                        Tag tag = Tag.createTag(name, generateConsistentColor(name));
                        Tag savedTag = tagRepository.save(tag);
                        tags.add(savedTag);
                        log.info("새 테마 태그 생성: {} (색상: {})", name, tag.getColor());
                    }
                }
            }
        }
        
        // demographics 처리
        if (demographics != null) {
            log.info("데모그래픽 태그 처리 시작: {}개", demographics.size());
            for (Map<String, Object> demographic : demographics) {
                String name = (String) demographic.get("name");
                if (name != null) {
                    // 기존 태그 찾기
                    Optional<Tag> existingTag = tagRepository.findByName(name);
                    if (existingTag.isPresent()) {
                        tags.add(existingTag.get());
                        log.debug("기존 데모그래픽 태그 사용: {}", name);
                    } else {
                        Tag tag = Tag.createTag(name, generateConsistentColor(name));
                        Tag savedTag = tagRepository.save(tag);
                        tags.add(savedTag);
                        log.info("새 데모그래픽 태그 생성: {} (색상: {})", name, tag.getColor());
                    }
                }
            }
        }
        
        log.info("태그 처리 완료: 총 {}개", tags.size());
        return tags;
    }
    
    /**
     * 감독 처리 및 저장
     */
    private Set<Director> processDirectors(Set<Director> directors) {
        Set<Director> processedDirectors = new java.util.HashSet<>();
        
        if (directors != null && !directors.isEmpty()) {
            log.info("🎬 감독 처리 시작: {}명", directors.size());
            for (Director director : directors) {
                Optional<Director> existingDirector = directorRepository.findByName(director.getName());
                if (existingDirector.isPresent()) {
                    processedDirectors.add(existingDirector.get());
                    log.debug("기존 감독 사용: {}", director.getName());
                } else {
                    Director savedDirector = directorRepository.save(director);
                    processedDirectors.add(savedDirector);
                    log.info("새 감독 생성: {}", director.getName());
                }
            }
            log.info("감독 처리 완료: 총 {}명", processedDirectors.size());
        }
        
        return processedDirectors;
    }
    
    /**
     * 성우 처리 및 저장
     */
    private Set<VoiceActor> processVoiceActors(Set<VoiceActor> voiceActors) {
        Set<VoiceActor> processedVoiceActors = new java.util.HashSet<>();
        
        if (voiceActors != null && !voiceActors.isEmpty()) {
            log.info("🎤 성우 처리 시작: {}명", voiceActors.size());
            for (VoiceActor voiceActor : voiceActors) {
                Optional<VoiceActor> existingVoiceActor = voiceActorRepository.findByName(voiceActor.getName());
                if (existingVoiceActor.isPresent()) {
                    processedVoiceActors.add(existingVoiceActor.get());
                    log.debug("기존 성우 사용: {}", voiceActor.getName());
                } else {
                    VoiceActor savedVoiceActor = voiceActorRepository.save(voiceActor);
                    processedVoiceActors.add(savedVoiceActor);
                    log.info("새 성우 생성: {}", voiceActor.getName());
                }
            }
            log.info("성우 처리 완료: 총 {}명", processedVoiceActors.size());
        }
        
        return processedVoiceActors;
    }
    
    /**
     * 캐릭터 처리 및 저장
     */
    private Set<Character> processCharacters(Set<Character> characters) {
        Set<Character> processedCharacters = new java.util.HashSet<>();
        
        if (characters != null && !characters.isEmpty()) {
            log.info("👤 캐릭터 처리 시작: {}명", characters.size());
            for (Character character : characters) {
                Optional<Character> existingCharacter = characterRepository.findByName(character.getName());
                if (existingCharacter.isPresent()) {
                    processedCharacters.add(existingCharacter.get());
                    log.debug("기존 캐릭터 사용: {}", character.getName());
                } else {
                    Character savedCharacter = characterRepository.save(character);
                    processedCharacters.add(savedCharacter);
                    log.info("새 캐릭터 생성: {}", character.getName());
                }
            }
            log.info("캐릭터 처리 완료: 총 {}명", processedCharacters.size());
        }
        
        return processedCharacters;
    }
    
    /**
     * 일관된 색상 생성 (태그 이름 기반)
     */
    private String generateConsistentColor(String name) {
        String[] colors = {
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7",
            "#DDA0DD", "#98D8C8", "#F7DC6F", "#BB8FCE", "#85C1E9",
            "#F8BBD9", "#A8E6CF", "#FFD3A5", "#FD6C9E", "#4ECDC4"
        };
        int hash = Math.abs(name.hashCode());
        int colorIndex = hash % colors.length;
        return colors[colorIndex];
    }
    
    // 제거: 미사용 메서드 generateRandomColor()
    
    /**
     * 수집 결과 통계 클래스
     */
    public static class CollectionResult {
        private final int successCount;
        private final int adultContentCount;
        private final int errorCount;
        
        public CollectionResult(int successCount, int adultContentCount, int errorCount) {
            this.successCount = successCount;
            this.adultContentCount = adultContentCount;
            this.errorCount = errorCount;
        }
        
        public int getSuccessCount() { return successCount; }
        public int getAdultContentCount() { return adultContentCount; }
        public int getErrorCount() { return errorCount; }
        public int getTotalProcessed() { return successCount + adultContentCount + errorCount; }
        
        @Override
        public String toString() {
            return String.format("성공: %d, 19금 제외: %d, 오류: %d, 총 처리: %d", 
                successCount, adultContentCount, errorCount, getTotalProcessed());
        }
    }
}
