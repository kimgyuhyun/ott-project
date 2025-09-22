package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.entity.Genre;
import com.ottproject.ottbackend.entity.Studio;
import com.ottproject.ottbackend.entity.Tag;
import com.ottproject.ottbackend.entity.Director;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.GenreRepository;
import com.ottproject.ottbackend.repository.StudioRepository;
import com.ottproject.ottbackend.repository.TagRepository;
import com.ottproject.ottbackend.repository.DirectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 애니메이션 배치 처리 서비스
 * 
 * 큰 흐름
 * - 저장된 애니메이션을 조회해서 연관 엔티티들을 처리한다
 * - 애니메이션 제목으로 검색해서 장르, 태그 등을 가져온다
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnimeBatchProcessor {
    
    private final AnimeRepository animeRepository;
    private final GenreRepository genreRepository;
    private final StudioRepository studioRepository;
    private final TagRepository tagRepository;
    private final DirectorRepository directorRepository;
    private final SimpleJikanDataMapper dataMapper;
    private final SimpleJikanApiService jikanApiService;
    
    /**
     * 저장된 애니메이션의 연관 엔티티들을 배치로 처리 (이미 가져온 데이터 사용)
     * - API 호출 없이 기존 jikanData 사용
     */
    @Transactional
    public void processAnimeAssociationsWithData(Long animeId, Map<String, Object> jikanData) {
        // 1. 저장된 애니메이션 조회
        Anime anime = animeRepository.findById(animeId).orElse(null);
        if (anime == null) {
            log.warn("애니메이션을 찾을 수 없음: ID {}", animeId);
            return;
        }
        
        log.info("🎬 애니메이션 연관 엔티티 처리 시작: {} (ID: {}) - 기존 데이터 사용", anime.getTitle(), animeId);
        
        // 2. 감독 처리
        try {
            processDirectors(anime, jikanData);
        } catch (Exception e) {
            log.warn("감독 처리 실패: 애니메이션 ID {} - {}", animeId, e.getMessage());
        }
        
        // 3. 장르 처리
        try {
            processGenres(anime, jikanData);
        } catch (Exception e) {
            log.warn("장르 처리 실패: 애니메이션 ID {} - {}", animeId, e.getMessage());
        }
        
        // 5. 스튜디오 처리
        try {
            processStudios(anime, jikanData);
        } catch (Exception e) {
            log.warn("스튜디오 처리 실패: 애니메이션 ID {} - {}", animeId, e.getMessage());
        }
        
        // 6. 태그 처리
        try {
            processTags(anime, jikanData);
        } catch (Exception e) {
            log.warn("태그 처리 실패: 애니메이션 ID {} - {}", animeId, e.getMessage());
        }
        
        // 7. 애니메이션 업데이트
        animeRepository.save(anime);
        
        log.info("✅ 애니메이션 연관 엔티티 처리 완료: {} (ID: {})", anime.getTitle(), animeId);
    }
    
    /**
     * 저장된 애니메이션의 연관 엔티티들을 배치로 처리 (API 재호출)
     * - 저장된 애니메이션의 malId로 Jikan API를 다시 호출해서 상세 정보 가져오기
     */
    @Transactional
    public void processAnimeAssociations(Long animeId) {
        // 1. 저장된 애니메이션 조회
        Anime anime = animeRepository.findById(animeId).orElse(null);
        if (anime == null) {
            log.warn("애니메이션을 찾을 수 없음: ID {}", animeId);
            return;
        }
        
        // 2. malId로 Jikan API 직접 조회해서 상세 정보 가져오기
        Long malId = anime.getMalId();
        log.info("🎬 애니메이션 연관 엔티티 처리 시작: {} (ID: {}, MAL ID: {})", anime.getTitle(), animeId, malId);
        if (malId == null) {
            log.warn("MAL ID가 없어서 연관 엔티티 처리 불가: ID {}", animeId);
            return;
        }
        
        // Jikan API에서 상세 정보 조회 (MAL ID로 직접 조회)
        var jikanDetails = jikanApiService.getAnimeDetails(malId);
        if (jikanDetails == null) {
            log.warn("Jikan API 조회 결과 없음: MAL ID {}", malId);
            return;
        }
        
        // DTO를 Map으로 변환
        Map<String, Object> jikanData = convertToMap(jikanDetails);
        
        // 3. 감독 처리
        try {
            processDirectors(anime, jikanData);
        } catch (Exception e) {
            log.warn("감독 처리 실패: 애니메이션 ID {} - {}", animeId, e.getMessage());
        }
        
        // 4. 장르 처리
        try {
            processGenres(anime, jikanData);
        } catch (Exception e) {
            log.warn("장르 처리 실패: 애니메이션 ID {} - {}", animeId, e.getMessage());
        }
        
        // 5. 스튜디오 처리
        try {
            processStudios(anime, jikanData);
        } catch (Exception e) {
            log.warn("스튜디오 처리 실패: 애니메이션 ID {} - {}", animeId, e.getMessage());
        }
        
        // 6. 태그 처리
        try {
            processTags(anime, jikanData);
        } catch (Exception e) {
            log.warn("태그 처리 실패: 애니메이션 ID {} - {}", animeId, e.getMessage());
        }
        
        // 7. 애니메이션 업데이트
        animeRepository.save(anime);
        
        log.info("✅ 애니메이션 연관 엔티티 처리 완료: {} (ID: {})", anime.getTitle(), animeId);
    }
    
    /**
     * 저장된 애니메이션의 연관 엔티티들을 배치로 처리 (감독 제외)
     * - 장르, 스튜디오, 태그만 처리
     */
    @Transactional
    public void processAnimeAssociationsWithoutDirectors(Long animeId) {
        // 1. 저장된 애니메이션 조회
        Anime anime = animeRepository.findById(animeId).orElse(null);
        if (anime == null) {
            log.warn("애니메이션을 찾을 수 없음: ID {}", animeId);
            return;
        }
        
        // 2. malId로 Jikan API 직접 조회해서 상세 정보 가져오기
        Long malId = anime.getMalId();
        log.info("🎬 애니메이션 연관 엔티티 처리 시작 (감독 제외): {} (ID: {}, MAL ID: {})", anime.getTitle(), animeId, malId);
        if (malId == null) {
            log.warn("MAL ID가 없어서 연관 엔티티 처리 불가: ID {}", animeId);
            return;
        }
        
        // Jikan API에서 상세 정보 조회 (MAL ID로 직접 조회)
        var jikanDetails = jikanApiService.getAnimeDetails(malId);
        if (jikanDetails == null) {
            log.warn("Jikan API 조회 결과 없음: MAL ID {}", malId);
            return;
        }
        
        // DTO를 Map으로 변환
        Map<String, Object> jikanData = convertToMap(jikanDetails);
        
        // 3. 장르 처리
        try {
            processGenres(anime, jikanData);
        } catch (Exception e) {
            log.warn("장르 처리 실패: 애니메이션 ID {} - {}", animeId, e.getMessage());
        }
        
        // 4. 스튜디오 처리
        try {
            processStudios(anime, jikanData);
        } catch (Exception e) {
            log.warn("스튜디오 처리 실패: 애니메이션 ID {} - {}", animeId, e.getMessage());
        }
        
        // 5. 태그 처리
        try {
            processTags(anime, jikanData);
        } catch (Exception e) {
            log.warn("태그 처리 실패: 애니메이션 ID {} - {}", animeId, e.getMessage());
        }
        
        // 6. 애니메이션 업데이트
        animeRepository.save(anime);
        
        log.info("✅ 애니메이션 연관 엔티티 처리 완료 (감독 제외): {} (ID: {})", anime.getTitle(), animeId);
    }
    
    /**
     * 감독 처리
     */
    @Transactional
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
        
        // 배치로 기존 감독 조회
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
            log.info("🆕 새 감독 생성 시작: {}명", newDirectorNames.size());
            Set<Director> newDirectors = newDirectorNames.stream()
                .map(name -> {
                    try {
                        Director director = Director.createDirector(name, "", "", "", "");
                        log.debug("감독 엔티티 생성됨: {}", director.getName());
                        return director;
                    } catch (Exception e) {
                        log.error("감독 생성 실패: {}", name, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            
            log.info("💾 감독 저장 시작: {}명", newDirectors.size());
            try {
                Set<Director> savedDirectors = new java.util.HashSet<>(directorRepository.saveAll(newDirectors));
                log.info("✅ 감독 저장 완료: {}명", savedDirectors.size());
                managedDirectors.addAll(savedDirectors);
            } catch (Exception e) {
                log.error("❌ 감독 저장 실패", e);
                throw e;
            }
        }
        
        anime.setDirectors(managedDirectors);
        log.info("🎬 감독 처리 완료: {}명 (기존: {}, 신규: {})", 
            managedDirectors.size(), existingDirectors.size(), newDirectorNames.size());
    }
    
    /**
     * 장르 처리
     */
    @Transactional
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
        
        // 배치로 기존 장르 조회
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
            
            Set<Genre> savedGenres = new java.util.HashSet<>(genreRepository.saveAll(newGenres));
            genres.addAll(savedGenres);
        }
        
        anime.setGenres(genres);
        log.info("🎭 장르 처리 완료: {}개 (기존: {}, 신규: {})", 
            genres.size(), existingGenres.size(), newGenreNames.size());
    }
    
    /**
     * 스튜디오 처리
     */
    @Transactional
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
        
        // 배치로 기존 스튜디오 조회
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
                .map(name -> Studio.createStudio(name, "", "", "", "", "", "Unknown"))
                .collect(Collectors.toSet());
            
            Set<Studio> savedStudios = new java.util.HashSet<>(studioRepository.saveAll(newStudios));
            studios.addAll(savedStudios);
        }
        
        anime.setStudios(studios);
        log.info("🏢 스튜디오 처리 완료: {}개 (기존: {}, 신규: {})", 
            studios.size(), existingStudios.size(), newStudioNames.size());
    }
    
    /**
     * 태그 처리
     */
    @Transactional
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
        
        // 배치로 기존 태그 조회
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
            
            Set<Tag> savedTags = new java.util.HashSet<>(tagRepository.saveAll(newTags));
            tags.addAll(savedTags);
        }
        
        anime.setTags(tags);
        log.info("🏷️ 태그 처리 완료: {}개 (기존: {}, 신규: {})", 
            tags.size(), existingTags.size(), newTagNames.size());
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
        jikanData.put("mal_id", details.getMal_id());
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
}
