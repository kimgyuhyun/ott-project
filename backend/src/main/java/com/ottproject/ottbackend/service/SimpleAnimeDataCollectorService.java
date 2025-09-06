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
import org.springframework.transaction.annotation.Isolation;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 안전한 애니메이션 데이터 수집 서비스
 * 
 * 핵심 원칙:
 * - 단일 트랜잭션으로 원자성 보장
 * - 배치 처리로 성능 최적화
 * - 메모리 효율적인 처리
 * - 강력한 에러 처리
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
    
    // 캐시를 위한 Map (메모리 효율성) - 스레드 로컬로 변경
    private final ThreadLocal<Map<String, Genre>> genreCache = ThreadLocal.withInitial(ConcurrentHashMap::new);
    private final ThreadLocal<Map<String, Studio>> studioCache = ThreadLocal.withInitial(ConcurrentHashMap::new);
    private final ThreadLocal<Map<String, Tag>> tagCache = ThreadLocal.withInitial(ConcurrentHashMap::new);
    private final ThreadLocal<Map<String, Director>> directorCache = ThreadLocal.withInitial(ConcurrentHashMap::new);
    
    /**
     * 단일 애니메이션 수집 - 안전한 트랜잭션 처리
     */
    @Transactional(rollbackFor = Exception.class, timeout = 300, isolation = Isolation.READ_COMMITTED) // 5분 타임아웃 (개발 환경)
    public boolean collectAnime(Long malId) {
        // 엣지 케이스 처리
        if (malId == null || malId <= 0) {
            log.warn("❌ 유효하지 않은 MAL ID: {}", malId);
            return false;
        }
        
        long startTime = System.currentTimeMillis();
        try {
            log.info("🎬 애니메이션 수집 시작: MAL ID {} (시간: {})", malId, startTime);
            
            // 1. API 호출 및 기본 검증
            var details = jikanApiService.getAnimeDetails(malId);
            if (details == null) {
                log.warn("❌ 애니메이션 데이터 없음: MAL ID {} (소요시간: {}ms)", malId, System.currentTimeMillis() - startTime);
                return false; // finally 블록에서 ThreadLocal 정리됨
            }
            
            // 2. DTO → Map 변환 (안전한 변환)
            Map<String, Object> jikanData;
            try {
                jikanData = convertToMap(details);
            } catch (Exception e) {
                log.error("❌ DTO 변환 실패: MAL ID {} (소요시간: {}ms) - {}", malId, System.currentTimeMillis() - startTime, e.getMessage(), e);
                return false; // finally 블록에서 ThreadLocal 정리됨
            }
            
            // 3. 중복 체크는 Anime 엔티티 생성 후에 수행 (title이 null일 수 있음)
            
            // 4. Anime 엔티티 생성 (안전한 생성)
            Anime anime;
            try {
                anime = dataMapper.mapToAnime(jikanData);
                if (anime == null) {
                    log.error("❌ Anime 엔티티 생성 실패: MAL ID {} (소요시간: {}ms) - null 반환", malId, System.currentTimeMillis() - startTime);
                    return false; // finally 블록에서 ThreadLocal 정리됨
                }
            } catch (Exception e) {
                log.error("❌ Anime 엔티티 생성 실패: MAL ID {} (소요시간: {}ms) - {}", malId, System.currentTimeMillis() - startTime, e.getMessage(), e);
                return false; // finally 블록에서 ThreadLocal 정리됨
            }
            
            // 5. 중복 체크 (한국어/영어/일본어 제목 모두 확인)
            boolean isDuplicate = false;
            String duplicateTitle = null;
            
            if (anime.getTitle() != null && animeRepository.existsByTitle(anime.getTitle())) {
                isDuplicate = true;
                duplicateTitle = anime.getTitle();
            } else if (anime.getTitleEn() != null && animeRepository.existsByTitle(anime.getTitleEn())) {
                isDuplicate = true;
                duplicateTitle = anime.getTitleEn();
            } else if (anime.getTitleJp() != null && animeRepository.existsByTitle(anime.getTitleJp())) {
                isDuplicate = true;
                duplicateTitle = anime.getTitleJp();
            }
            
            if (isDuplicate) {
                log.info("⚠️ 이미 존재하는 애니메이션: {} (MAL ID: {}, 소요시간: {}ms)", duplicateTitle, malId, System.currentTimeMillis() - startTime);
                return false; // finally 블록에서 ThreadLocal 정리됨
            }
            
            log.info("✅ 애니메이션 엔티티 생성: {} (MAL ID: {}, 소요시간: {}ms)", anime.getTitle(), malId, System.currentTimeMillis() - startTime);
            
            // 6. 애니메이션 먼저 DB 저장 (중복 제약조건으로 Race Condition 방지)
            try {
                anime = animeRepository.save(anime);
                log.info("💾 애니메이션 DB 저장 완료: ID {} (MAL ID: {}, 소요시간: {}ms)", anime.getId(), malId, System.currentTimeMillis() - startTime);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                log.warn("⚠️ 중복 제약조건 위반: {} (MAL ID: {}, 소요시간: {}ms) - 다른 스레드에서 이미 저장됨", anime.getTitle(), malId, System.currentTimeMillis() - startTime);
                return false; // finally 블록에서 ThreadLocal 정리됨
            }
            
            // 7. 연관 엔티티 처리 (저장된 애니메이션 ID로 처리) - 실패 시 전체 롤백
            try {
                processAssociatedEntities(anime, jikanData, malId);
            } catch (Exception e) {
                log.error("❌ 연관 엔티티 처리 실패: MAL ID {} (소요시간: {}ms) - {}", malId, System.currentTimeMillis() - startTime, e.getMessage(), e);
                // ThreadLocal 정리를 위해 finally 블록에서 처리되도록 RuntimeException 전파
                // 이 예외는 @Transactional에 의해 롤백을 유발하지만 finally 블록은 실행됨
                throw new RuntimeException("연관 엔티티 처리 중 오류 발생", e);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("🎉 애니메이션 수집 완료: {} (MAL ID: {}, 소요시간: {}ms)", anime.getTitle(), malId, duration);
            
            return true;
            
        } catch (AdultContentException e) {
            log.info("🚫 19금 콘텐츠 제외: MAL ID {} (소요시간: {}ms) - {}", malId, System.currentTimeMillis() - startTime, e.getMessage());
            return false; // finally 블록에서 ThreadLocal 정리됨
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("❌ DB 접근 오류: MAL ID {} (소요시간: {}ms) - {}", malId, System.currentTimeMillis() - startTime, e.getMessage());
            // DB 오류는 롤백되어야 하므로 RuntimeException으로 전파
            // finally 블록은 RuntimeException 전파 전에 실행되므로 ThreadLocal 정리 보장됨
            throw new RuntimeException("데이터베이스 오류 발생", e);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("❌ API 연결 오류: MAL ID {} (소요시간: {}ms) - {}", malId, System.currentTimeMillis() - startTime, e.getMessage());
            return false; // finally 블록에서 ThreadLocal 정리됨
        } catch (Exception e) {
            log.error("❌ 애니메이션 수집 실패: MAL ID {} (소요시간: {}ms) - {}", malId, System.currentTimeMillis() - startTime, e.getMessage(), e);
            // 예상치 못한 오류는 롤백되어야 하므로 RuntimeException으로 전파
            // finally 블록은 RuntimeException 전파 전에 실행되므로 ThreadLocal 정리 보장됨
            throw new RuntimeException("애니메이션 수집 중 예상치 못한 오류 발생", e);
        } finally {
            // ThreadLocal 정리로 메모리 누수 방지 (모든 경우에 실행)
            // RuntimeException 전파 시에도 이 블록이 실행됨
            clearThreadLocalCaches();
        }
    }
    
    /**
     * 연관 엔티티들을 배치로 처리
     */
    private void processAssociatedEntities(Anime anime, Map<String, Object> jikanData, Long malId) {
        // 1. 감독 처리 (안전한 처리)
        try {
            processDirectors(anime, jikanData);
        } catch (Exception e) {
            log.warn("감독 처리 실패: MAL ID {} - 기본 데이터만 저장", malId, e);
        }
        
        // 2. 성우/캐릭터 처리 (안전한 처리)
        try {
            processVoiceActorsAndCharacters(anime, malId);
        } catch (Exception e) {
            log.warn("성우/캐릭터 처리 실패: MAL ID {} - 기본 데이터만 저장", malId, e);
        }
        
        // 3. 장르 처리 (안전한 처리)
        try {
            processGenres(anime, jikanData);
        } catch (Exception e) {
            log.warn("장르 처리 실패: MAL ID {} - 기본 데이터만 저장", malId, e);
        }
        
        // 4. 스튜디오 처리 (안전한 처리)
        try {
            processStudios(anime, jikanData);
        } catch (Exception e) {
            log.warn("스튜디오 처리 실패: MAL ID {} - 기본 데이터만 저장", malId, e);
        }
        
        // 5. 태그 처리 (안전한 처리)
        try {
            processTags(anime, jikanData);
        } catch (Exception e) {
            log.warn("태그 처리 실패: MAL ID {} - 기본 데이터만 저장", malId, e);
        }
    }
    
    /**
     * 감독 처리 - 배치 최적화로 N+1 쿼리 방지
     */
    private void processDirectors(Anime anime, Map<String, Object> jikanData) {
        Set<Director> directors = dataMapper.mapToDirectors(jikanData);
        if (directors == null || directors.isEmpty()) {
            anime.setDirectors(new java.util.HashSet<>());
            return;
        }
        
        // 모든 감독 이름 수집
        Set<String> directorNames = directors.stream()
            .map(Director::getName)
            .filter(name -> name != null && !name.trim().isEmpty())
            .collect(Collectors.toSet());
        
        if (directorNames.isEmpty()) {
            anime.setDirectors(new java.util.HashSet<>());
            return;
        }
        
        // 배치로 기존 감독 조회 (N+1 쿼리 방지)
        Set<Director> existingDirectors = directorRepository.findByNameIn(directorNames);
        Map<String, Director> existingDirectorMap = existingDirectors.stream()
            .collect(Collectors.toMap(Director::getName, director -> director));
        
        // 기존 감독과 새 감독 분리
        Set<Director> managedDirectors = new java.util.HashSet<>(existingDirectors);
        Set<String> newDirectorNames = directorNames.stream()
            .filter(name -> !existingDirectorMap.containsKey(name))
            .collect(Collectors.toSet());
        
        // 새 감독만 배치 생성
        if (!newDirectorNames.isEmpty()) {
            Set<Director> newDirectors = newDirectorNames.stream()
                .map(name -> Director.createDirector(name, "", "", "", ""))
                .collect(Collectors.toSet());
            
            // 배치 저장
            Set<Director> savedDirectors = new java.util.HashSet<>(directorRepository.saveAll(newDirectors));
            managedDirectors.addAll(savedDirectors);
        }
        
        anime.setDirectors(managedDirectors);
        log.info("🎬 감독 처리 완료: {}명 (기존: {}, 신규: {})", 
            managedDirectors.size(), existingDirectors.size(), newDirectorNames.size());
    }
    
    /**
     * 성우/캐릭터 처리 - 배치 최적화로 N+1 쿼리 방지 (실패해도 전체 프로세스 중단하지 않음)
     */
    private void processVoiceActorsAndCharacters(Anime anime, Long malId) {
        try {
            var charactersDto = jikanApiService.getAnimeCharacters(malId);
            if (charactersDto == null || charactersDto.getData() == null) {
                log.warn("캐릭터 정보 없음: MAL ID {} - 기본 데이터만 저장", malId);
                return;
            }
            
            // 안전한 Map 변환 (NullPointerException 방지)
            Map<String, Object> charactersData;
            try {
                charactersData = convertCharactersToMap(charactersDto);
            } catch (Exception e) {
                log.warn("캐릭터 데이터 변환 실패: MAL ID {} - 기본 데이터만 저장", malId, e);
                return;
            }
            
            // 성우 처리 - 배치 최적화
            Set<VoiceActor> voiceActors;
            try {
                voiceActors = dataMapper.mapToVoiceActors(charactersData);
                if (voiceActors == null) voiceActors = new java.util.HashSet<>();
            } catch (Exception e) {
                log.warn("성우 매핑 실패: MAL ID {} - 기본 데이터만 저장", malId, e);
                voiceActors = new java.util.HashSet<>();
            }
            
            Set<VoiceActor> managedVoiceActors = processVoiceActorsBatch(voiceActors);
            anime.setVoiceActors(managedVoiceActors);
            
            // 캐릭터 처리 - 배치 최적화
            Set<Character> characters;
            try {
                characters = dataMapper.mapToCharacters(charactersData);
                if (characters == null) characters = new java.util.HashSet<>();
            } catch (Exception e) {
                log.warn("캐릭터 매핑 실패: MAL ID {} - 기본 데이터만 저장", malId, e);
                characters = new java.util.HashSet<>();
            }
            
            Set<Character> managedCharacters = processCharactersBatch(characters);
            anime.setCharacters(managedCharacters);
            
            log.info("🎤 성우/캐릭터 처리 완료: 성우 {}명, 캐릭터 {}명", 
                managedVoiceActors.size(), managedCharacters.size());
                
        } catch (Exception e) {
            log.warn("성우/캐릭터 처리 실패: MAL ID {} - 기본 데이터만 저장", malId, e);
            // 성우/캐릭터 실패는 전체 실패로 이어지지 않도록 처리
        }
    }
    
    /**
     * 장르 처리 - 배치 최적화로 N+1 쿼리 방지
     */
    private void processGenres(Anime anime, Map<String, Object> jikanData) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> genresList = (List<Map<String, Object>>) jikanData.get("genres");
        if (genresList == null || genresList.isEmpty()) {
            anime.setGenres(new java.util.HashSet<>());
            return;
        }
        
        // 모든 장르 이름 수집
        Set<String> genreNames = genresList.stream()
            .map(genreMap -> (String) genreMap.get("name"))
            .filter(name -> name != null && !name.trim().isEmpty())
            .collect(Collectors.toSet());
        
        if (genreNames.isEmpty()) {
            anime.setGenres(new java.util.HashSet<>());
            return;
        }
        
        // 배치로 기존 장르 조회 (N+1 쿼리 방지)
        Set<Genre> existingGenres = genreRepository.findByNameIn(genreNames);
        Map<String, Genre> existingGenreMap = existingGenres.stream()
            .collect(Collectors.toMap(Genre::getName, genre -> genre));
        
        // 기존 장르와 새 장르 분리
        Set<Genre> genres = new java.util.HashSet<>(existingGenres);
        Set<String> newGenreNames = genreNames.stream()
            .filter(name -> !existingGenreMap.containsKey(name))
            .collect(Collectors.toSet());
        
        // 새 장르만 배치 생성
        if (!newGenreNames.isEmpty()) {
            Set<Genre> newGenres = newGenreNames.stream()
                .map(name -> Genre.createGenre(name, "", generateConsistentColor(name)))
                .collect(Collectors.toSet());
            
            // 배치 저장
            Set<Genre> savedGenres = new java.util.HashSet<>(genreRepository.saveAll(newGenres));
            genres.addAll(savedGenres);
        }
        
        anime.setGenres(genres);
        log.info("🎭 장르 처리 완료: {}개 (기존: {}, 신규: {})", 
            genres.size(), existingGenres.size(), newGenreNames.size());
    }
    
    /**
     * 스튜디오 처리 - 배치 최적화로 N+1 쿼리 방지
     */
    private void processStudios(Anime anime, Map<String, Object> jikanData) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> studiosList = (List<Map<String, Object>>) jikanData.get("studios");
        if (studiosList == null || studiosList.isEmpty()) {
            anime.setStudios(new java.util.HashSet<>());
            return;
        }
        
        // 모든 스튜디오 이름 수집
        Set<String> studioNames = studiosList.stream()
            .map(studioMap -> (String) studioMap.get("name"))
            .filter(name -> name != null && !name.trim().isEmpty())
            .collect(Collectors.toSet());
        
        if (studioNames.isEmpty()) {
            anime.setStudios(new java.util.HashSet<>());
            return;
        }
        
        // 배치로 기존 스튜디오 조회 (N+1 쿼리 방지)
        Set<Studio> existingStudios = studioRepository.findByNameIn(studioNames);
        Map<String, Studio> existingStudioMap = existingStudios.stream()
            .collect(Collectors.toMap(Studio::getName, studio -> studio));
        
        // 기존 스튜디오와 새 스튜디오 분리
        Set<Studio> studios = new java.util.HashSet<>(existingStudios);
        Set<String> newStudioNames = studioNames.stream()
            .filter(name -> !existingStudioMap.containsKey(name))
            .collect(Collectors.toSet());
        
        // 새 스튜디오만 배치 생성
        if (!newStudioNames.isEmpty()) {
            Set<Studio> newStudios = newStudioNames.stream()
                .map(name -> Studio.createStudio(name, "", "", "", "", "", ""))
                .collect(Collectors.toSet());
            
            // 배치 저장
            Set<Studio> savedStudios = new java.util.HashSet<>(studioRepository.saveAll(newStudios));
            studios.addAll(savedStudios);
        }
        
        anime.setStudios(studios);
        log.info("🏢 스튜디오 처리 완료: {}개 (기존: {}, 신규: {})", 
            studios.size(), existingStudios.size(), newStudioNames.size());
    }
    
    /**
     * 태그 처리 - 배치 최적화로 N+1 쿼리 방지
     */
    private void processTags(Anime anime, Map<String, Object> jikanData) {
        // 안전한 타입 캐스팅
        List<Map<String, Object>> themesList = null;
        List<Map<String, Object>> demographicsList = null;
        
        try {
            Object themesObj = jikanData.get("themes");
            if (themesObj instanceof List) {
                themesList = (List<Map<String, Object>>) themesObj;
            }
        } catch (ClassCastException e) {
            log.warn("themes 타입 캐스팅 실패: {}", e.getMessage());
        }
        
        try {
            Object demographicsObj = jikanData.get("demographics");
            if (demographicsObj instanceof List) {
                demographicsList = (List<Map<String, Object>>) demographicsObj;
            }
        } catch (ClassCastException e) {
            log.warn("demographics 타입 캐스팅 실패: {}", e.getMessage());
        }
        
        // 모든 태그 이름 수집
        Set<String> tagNames = new java.util.HashSet<>();
        
        if (themesList != null) {
            themesList.stream()
                .map(themeMap -> (String) themeMap.get("name"))
                .filter(name -> name != null && !name.trim().isEmpty())
                .forEach(tagNames::add);
        }
        
        if (demographicsList != null) {
            demographicsList.stream()
                .map(demoMap -> (String) demoMap.get("name"))
                .filter(name -> name != null && !name.trim().isEmpty())
                .forEach(tagNames::add);
        }
        
        if (tagNames.isEmpty()) {
            anime.setTags(new java.util.HashSet<>());
            return;
        }
        
        // 배치로 기존 태그 조회 (N+1 쿼리 방지)
        Set<Tag> existingTags = tagRepository.findByNameIn(tagNames);
        Map<String, Tag> existingTagMap = existingTags.stream()
            .collect(Collectors.toMap(Tag::getName, tag -> tag));
        
        // 기존 태그와 새 태그 분리
        Set<Tag> tags = new java.util.HashSet<>(existingTags);
        Set<String> newTagNames = tagNames.stream()
            .filter(name -> !existingTagMap.containsKey(name))
            .collect(Collectors.toSet());
        
        // 새 태그만 배치 생성
        if (!newTagNames.isEmpty()) {
            Set<Tag> newTags = newTagNames.stream()
                .map(name -> Tag.createTag(name, generateConsistentColor(name)))
                .collect(Collectors.toSet());
            
            // 배치 저장
            Set<Tag> savedTags = new java.util.HashSet<>(tagRepository.saveAll(newTags));
            tags.addAll(savedTags);
        }
        
        anime.setTags(tags);
        log.info("🏷️ 태그 처리 완료: {}개 (기존: {}, 신규: {})", 
            tags.size(), existingTags.size(), newTagNames.size());
    }
    
    /**
     * 성우 배치 처리 - N+1 쿼리 방지
     */
    private Set<VoiceActor> processVoiceActorsBatch(Set<VoiceActor> voiceActors) {
        if (voiceActors.isEmpty()) {
            return new java.util.HashSet<>();
        }
        
        // 모든 성우 이름 수집
        Set<String> voiceActorNames = voiceActors.stream()
            .map(VoiceActor::getName)
            .filter(name -> name != null && !name.trim().isEmpty())
            .collect(Collectors.toSet());
        
        if (voiceActorNames.isEmpty()) {
            return new java.util.HashSet<>();
        }
        
        // 배치로 기존 성우 조회 (N+1 쿼리 방지)
        Set<VoiceActor> existingVoiceActors = voiceActorRepository.findByNameIn(voiceActorNames);
        Map<String, VoiceActor> existingVoiceActorMap = existingVoiceActors.stream()
            .collect(Collectors.toMap(VoiceActor::getName, voiceActor -> voiceActor));
        
        // 기존 성우와 새 성우 분리
        Set<VoiceActor> managedVoiceActors = new java.util.HashSet<>(existingVoiceActors);
        Set<VoiceActor> newVoiceActors = voiceActors.stream()
            .filter(voiceActor -> !existingVoiceActorMap.containsKey(voiceActor.getName()))
            .collect(Collectors.toSet());
        
        // 새 성우만 배치 생성
        if (!newVoiceActors.isEmpty()) {
            Set<VoiceActor> savedVoiceActors = new java.util.HashSet<>(voiceActorRepository.saveAll(newVoiceActors));
            managedVoiceActors.addAll(savedVoiceActors);
        }
        
        return managedVoiceActors;
    }
    
    /**
     * 캐릭터 배치 처리 - N+1 쿼리 방지
     */
    private Set<Character> processCharactersBatch(Set<Character> characters) {
        if (characters.isEmpty()) {
            return new java.util.HashSet<>();
        }
        
        // 모든 캐릭터 이름 수집
        Set<String> characterNames = characters.stream()
            .map(Character::getName)
            .filter(name -> name != null && !name.trim().isEmpty())
            .collect(Collectors.toSet());
        
        if (characterNames.isEmpty()) {
            return new java.util.HashSet<>();
        }
        
        // 배치로 기존 캐릭터 조회 (N+1 쿼리 방지)
        Set<Character> existingCharacters = characterRepository.findByNameIn(characterNames);
        Map<String, Character> existingCharacterMap = existingCharacters.stream()
            .collect(Collectors.toMap(Character::getName, character -> character));
        
        // 기존 캐릭터와 새 캐릭터 분리
        Set<Character> managedCharacters = new java.util.HashSet<>(existingCharacters);
        Set<Character> newCharacters = characters.stream()
            .filter(character -> !existingCharacterMap.containsKey(character.getName()))
            .collect(Collectors.toSet());
        
        // 새 캐릭터만 배치 생성
        if (!newCharacters.isEmpty()) {
            Set<Character> savedCharacters = new java.util.HashSet<>(characterRepository.saveAll(newCharacters));
            managedCharacters.addAll(savedCharacters);
        }
        
        return managedCharacters;
    }
    
    /**
     * 캐시에서 조회하거나 새로 생성
     */
    // getOrCreateGenre, getOrCreateStudio, getOrCreateTag는 배치 처리로 대체됨
    
    
    /**
     * ThreadLocal 캐시 정리 (메모리 누수 방지) - 개별 정리로 부분 실패 방지
     */
    private void clearThreadLocalCaches() {
        // 각 ThreadLocal을 개별적으로 정리하여 부분 실패 시에도 최대한 정리
        // 순서대로 정리하여 의존성 문제 방지
        clearThreadLocal(genreCache, "genreCache");
        clearThreadLocal(studioCache, "studioCache");
        clearThreadLocal(tagCache, "tagCache");
        clearThreadLocal(directorCache, "directorCache");
        
        // 정리 완료 로그 (개발 환경)
        log.debug("🧹 ThreadLocal 캐시 정리 완료 (시간: {})", System.currentTimeMillis());
    }
    
    /**
     * 개별 ThreadLocal 정리 (안전한 정리)
     */
    private void clearThreadLocal(ThreadLocal<?> threadLocal, String name) {
        try {
            threadLocal.remove();
        } catch (Exception e) {
            log.warn("ThreadLocal {} 정리 중 오류 발생", name, e);
        }
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
    
    /**
     * DTO를 Map으로 변환
     */
    private Map<String, Object> convertToMap(com.ottproject.ottbackend.dto.jikan.AnimeDetailsJikanDto.Data details) {
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
        
        // staff
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
            
        return jikanData;
    }
    
    /**
     * 캐릭터 DTO를 Map으로 변환
     */
    private Map<String, Object> convertCharactersToMap(com.ottproject.ottbackend.dto.jikan.AnimeCharactersJikanDto charactersDto) {
            Map<String, Object> charactersData = new java.util.HashMap<>();
            List<Map<String, Object>> charactersList = new java.util.ArrayList<>();
        
        if (charactersDto.getData() != null) {
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
        return charactersData;
    }
    
    
    /**
     * 인기 애니메이션 일괄 수집 - 안전한 배치 처리
     */
    @Transactional(rollbackFor = Exception.class)
    public CollectionResult collectPopularAnime(int limit) {
        log.info("🚀 인기 애니메이션 일괄 수집 시작: {}개", limit);
        
        try {
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
                        log.warn("⚠️ [{}/{}] 수집 실패 (중복 또는 기타): MAL ID {}", i + 1, popularIds.size(), malId);
                }
                
                // Rate limit 대응
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
            
        } catch (Exception e) {
            log.error("❌ 배치 수집 중 치명적 오류 발생", e);
            throw new RuntimeException("배치 수집 실패", e);
        }
    }
    
    /**
     * 캐시 초기화 (메모리 관리)
     */
    public void clearCache() {
        genreCache.get().clear();
        studioCache.get().clear();
        tagCache.get().clear();
        directorCache.get().clear();
        log.info("🧹 캐시 초기화 완료");
    }
    
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