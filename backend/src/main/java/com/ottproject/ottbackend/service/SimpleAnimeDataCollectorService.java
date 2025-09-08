package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.entity.VoiceActor;
import com.ottproject.ottbackend.entity.Character;
import com.ottproject.ottbackend.exception.AdultContentException;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.VoiceActorRepository;
import com.ottproject.ottbackend.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.ottproject.ottbackend.dto.jikan.AnimeCharactersJikanDto;

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
    private final VoiceActorRepository voiceActorRepository;
    private final CharacterRepository characterRepository;
    private final AnimeBatchProcessor animeBatchProcessor;
    private final PlatformTransactionManager transactionManager;
    
    
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
            
            // 7. 연관 엔티티 처리 (이미 가져온 jikanData 사용) - 실패 시 전체 롤백
            try {
                animeBatchProcessor.processAnimeAssociationsWithData(anime.getId(), jikanData);
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
        }
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
        
        // 기존 성우를 이름별로 그룹화 (같은 이름의 성우가 여러 개 있을 수 있음)
        Map<String, List<VoiceActor>> existingVoiceActorMap = existingVoiceActors.stream()
            .collect(Collectors.groupingBy(VoiceActor::getName));
        
        // 기존 성우와 새 성우 분리
        Set<VoiceActor> managedVoiceActors = new java.util.HashSet<>(existingVoiceActors);
        Set<VoiceActor> newVoiceActors = new java.util.HashSet<>();
        
        for (VoiceActor voiceActor : voiceActors) {
            String name = voiceActor.getName();
            if (name != null && !name.trim().isEmpty()) {
                List<VoiceActor> existingWithSameName = existingVoiceActorMap.get(name);
                if (existingWithSameName == null || existingWithSameName.isEmpty()) {
                    // 같은 이름의 성우가 없으면 새로 추가
                    newVoiceActors.add(voiceActor);
                } else {
                    // 같은 이름의 성우가 있으면 첫 번째 것을 사용
                    managedVoiceActors.add(existingWithSameName.get(0));
                }
            }
        }
        
        // 새 성우만 배치 생성
        if (!newVoiceActors.isEmpty()) {
            try {
                Set<VoiceActor> savedVoiceActors = new java.util.HashSet<>(voiceActorRepository.saveAll(newVoiceActors));
                managedVoiceActors.addAll(savedVoiceActors);
            } catch (Exception e) {
                log.warn("성우 저장 중 오류 발생, 개별 저장 시도: {}", e.getMessage());
                // 개별 저장으로 fallback
                for (VoiceActor voiceActor : newVoiceActors) {
                    try {
                        VoiceActor saved = voiceActorRepository.save(voiceActor);
                        managedVoiceActors.add(saved);
                    } catch (Exception ex) {
                        log.warn("성우 개별 저장 실패: {} - {}", voiceActor.getName(), ex.getMessage());
                    }
                }
            }
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
        
        // 기존 캐릭터를 이름별로 그룹화 (같은 이름의 캐릭터가 여러 개 있을 수 있음)
        Map<String, List<Character>> existingCharacterMap = existingCharacters.stream()
            .collect(Collectors.groupingBy(Character::getName));
        
        // 기존 캐릭터와 새 캐릭터 분리
        Set<Character> managedCharacters = new java.util.HashSet<>(existingCharacters);
        Set<Character> newCharacters = new java.util.HashSet<>();
        
        for (Character character : characters) {
            String name = character.getName();
            if (name != null && !name.trim().isEmpty()) {
                List<Character> existingWithSameName = existingCharacterMap.get(name);
                if (existingWithSameName == null || existingWithSameName.isEmpty()) {
                    // 같은 이름의 캐릭터가 없으면 새로 추가
                    newCharacters.add(character);
                } else {
                    // 같은 이름의 캐릭터가 있으면 첫 번째 것을 사용
                    managedCharacters.add(existingWithSameName.get(0));
                }
            }
        }
        
        // 새 캐릭터만 배치 생성
        if (!newCharacters.isEmpty()) {
            try {
                Set<Character> savedCharacters = new java.util.HashSet<>(characterRepository.saveAll(newCharacters));
                managedCharacters.addAll(savedCharacters);
            } catch (Exception e) {
                log.warn("캐릭터 저장 중 오류 발생, 개별 저장 시도: {}", e.getMessage());
                // 개별 저장으로 fallback
                for (Character character : newCharacters) {
                    try {
                        Character saved = characterRepository.save(character);
                        managedCharacters.add(saved);
                    } catch (Exception ex) {
                        log.warn("캐릭터 개별 저장 실패: {} - {}", character.getName(), ex.getMessage());
                    }
                }
            }
        }
        
        return managedCharacters;
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
    
    /**
     * 성우/캐릭터 비동기 처리 시작
     * - 메인 수집 속도에 영향 없도록 비동기로 처리
     */
    public void processVoiceActorsAndCharactersAsync(Long animeId, Long malId) {
        // 비동기 처리를 위한 별도 메서드 호출
        processVoiceActorsAndCharactersInBackground(animeId, malId);
    }
    
    /**
     * 성우/캐릭터 백그라운드 처리
     * - 완전히 독립적인 트랜잭션에서 실행
     * - 재시도 로직 포함
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processVoiceActorsAndCharactersInBackground(Long animeId, Long malId) {
        try {
            // 저장된 애니메이션 조회
            Anime anime = animeRepository.findById(animeId).orElse(null);
            if (anime == null) {
                log.warn("애니메이션을 찾을 수 없음: ID {}", animeId);
                return;
            }
            
            // Jikan API에서 캐릭터/성우 정보 조회
            AnimeCharactersJikanDto charactersDto = jikanApiService.getAnimeCharacters(malId);
            if (charactersDto == null || charactersDto.getData() == null) {
                log.warn("캐릭터/성우 데이터 없음: MAL ID {}", malId);
                return;
            }
            
            // DTO를 Map으로 변환
            Map<String, Object> charactersData = convertCharactersToMap(charactersDto);
            
            // 성우 처리 - 마스터만 upsert (조인/애니 매핑 금지)
            Set<VoiceActor> voiceActors = dataMapper.mapToVoiceActors(charactersData);
            if (!voiceActors.isEmpty()) {
                Set<VoiceActor> managedVoiceActors = processVoiceActorsBatch(voiceActors);
                log.info("성우 마스터 upsert 완료: {}명 (MAL ID {})", managedVoiceActors.size(), malId);
            }
            
            // 캐릭터 처리 - 마스터만 upsert (조인/애니 매핑 금지)
            Set<Character> characters = dataMapper.mapToCharacters(charactersData);
            if (!characters.isEmpty()) {
                Set<Character> managedCharacters = processCharactersBatch(characters);
                log.info("캐릭터 마스터 upsert 완료: {}명 (MAL ID {})", managedCharacters.size(), malId);
            }
            
            
        } catch (Exception e) {
            log.error("성우/캐릭터 처리 실패: MAL ID {} - 재시도 예정", malId, e);
            
            // 재시도 로직 (최대 3회, 지수 백오프)
            retryVoiceActorsAndCharacters(animeId, malId, 1);
        }
    }
    
    /**
     * 성우/캐릭터 처리 재시도 로직
     */
    private void retryVoiceActorsAndCharacters(Long animeId, Long malId, int attempt) {
        if (attempt > 3) {
            log.error("성우/캐릭터 처리 최종 실패: MAL ID {} (재시도 3회 초과)", malId);
            return;
        }
        
        try {
            // 지수 백오프: 2^attempt 초 대기
            long delayMs = (long) Math.pow(2, attempt) * 1000;
            Thread.sleep(delayMs);
            
            log.info("성우/캐릭터 처리 재시도: MAL ID {} (시도 {}/3)", malId, attempt);
            processVoiceActorsAndCharactersInBackground(animeId, malId);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("성우/캐릭터 재시도 중 인터럽트: MAL ID {}", malId);
        } catch (Exception e) {
            log.error("성우/캐릭터 재시도 실패: MAL ID {} (시도 {}/3)", malId, attempt, e);
            retryVoiceActorsAndCharacters(animeId, malId, attempt + 1);
        }
    }
    
    /**
     * 캐릭터 DTO를 Map으로 변환
     */
    public Map<String, Object> convertCharactersToMap(AnimeCharactersJikanDto charactersDto) {
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
     * 외부에서 안전하게 호출할 수 있도록 Jikan 캐릭터 조회를 노출
     */
    public AnimeCharactersJikanDto getAnimeCharactersFromJikan(Long malId) {
        return jikanApiService.getAnimeCharacters(malId);
    }

    /**
     * charactersData에서 캐릭터 이름들을 추출하여 기존 캐릭터 엔티티로 매핑
     */
    public Set<Character> mapToExistingCharacters(Map<String, Object> charactersData, CharacterRepository characterRepository) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> charactersList = (List<Map<String, Object>>) charactersData.getOrDefault("characters", java.util.List.of());
        Set<String> names = charactersList.stream()
            .map(m -> (Map<String, Object>) m.getOrDefault("character", java.util.Map.of()))
            .map(cm -> (String) cm.getOrDefault("name", null))
            .filter(n -> n != null && !n.isBlank())
            .collect(Collectors.toSet());
        if (names.isEmpty()) return java.util.Set.of();
        return characterRepository.findByNameIn(names);
    }

    /**
     * charactersData에서 성우 이름들을 추출하여 기존 성우 엔티티로 매핑
     */
    public Set<VoiceActor> mapToExistingVoiceActors(Map<String, Object> charactersData, VoiceActorRepository voiceActorRepository) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> charactersList = (List<Map<String, Object>>) charactersData.getOrDefault("characters", java.util.List.of());
        Set<String> names = new java.util.HashSet<>();
        for (Map<String, Object> item : charactersList) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> vaList = (List<Map<String, Object>>) item.getOrDefault("voice_actors", java.util.List.of());
            for (Map<String, Object> va : vaList) {
                @SuppressWarnings("unchecked")
                Map<String, Object> person = (Map<String, Object>) va.getOrDefault("person", java.util.Map.of());
                String name = (String) person.get("name");
                if (name != null && !name.isBlank()) names.add(name);
            }
        }
        if (names.isEmpty()) return java.util.Set.of();
        return voiceActorRepository.findByNameIn(names);
    }

    /**
     * 캐릭터-성우 조인 upsert (마스터는 이미 존재한다고 가정)
     */
    @Transactional
    public void upsertCharacterVoiceActorJoins(Map<String, Object> charactersData,
                                               CharacterRepository characterRepository,
                                               VoiceActorRepository voiceActorRepository) {
        Set<Character> existingCharacters = mapToExistingCharacters(charactersData, characterRepository);
        Set<VoiceActor> existingVoiceActors = mapToExistingVoiceActors(charactersData, voiceActorRepository);

        if (existingCharacters.isEmpty() || existingVoiceActors.isEmpty()) {
            log.info("캐릭터-성우 조인 스킵: character={}, voiceActor={}", existingCharacters.size(), existingVoiceActors.size());
            return;
        }

        // 이름 기준 빠른 조회 맵 구성 (트리밍/정규화)
        java.util.Map<String, Character> nameToCharacter = existingCharacters.stream()
            .filter(c -> c.getName() != null)
            .collect(Collectors.toMap(c -> c.getName().trim(), c -> c, (a, b) -> a));
        java.util.Map<String, VoiceActor> nameToVoice = existingVoiceActors.stream()
            .filter(v -> v.getName() != null)
            .collect(Collectors.toMap(v -> v.getName().trim(), v -> v, (a, b) -> a));

        int savedCount = 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> charactersList = (List<Map<String, Object>>) charactersData.getOrDefault("characters", java.util.List.of());
        for (Map<String, Object> item : charactersList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cm = (Map<String, Object>) item.getOrDefault("character", java.util.Map.of());
            String cnameRaw = (String) cm.getOrDefault("name", null);
            String cname = cnameRaw == null ? null : cnameRaw.trim();
            Character character = cname == null ? null : nameToCharacter.get(cname);
            if (character == null) {
                log.debug("캐릭터 매칭 실패: name={}", cname);
                continue;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> vaList = (List<Map<String, Object>>) item.getOrDefault("voice_actors", java.util.List.of());
            if (vaList.isEmpty()) continue;

            java.util.Set<VoiceActor> current = character.getVoiceActors() != null ? new java.util.HashSet<>(character.getVoiceActors()) : new java.util.HashSet<>();
            int before = current.size();
            for (Map<String, Object> va : vaList) {
                @SuppressWarnings("unchecked")
                Map<String, Object> person = (Map<String, Object>) va.getOrDefault("person", java.util.Map.of());
                String vnameRaw = (String) person.get("name");
                String vname = vnameRaw == null ? null : vnameRaw.trim();
                if (vname == null || vname.isBlank()) continue;
                VoiceActor exist = nameToVoice.get(vname);
                if (exist == null) {
                    log.debug("성우 매칭 실패: name={}", vname);
                    continue;
                }
                current.add(exist);
            }
            if (current.size() > before) {
                character.setVoiceActors(current);
                characterRepository.save(character);
                savedCount++;
                log.debug("캐릭터-성우 조인 저장: characterId={}, before={}, after={}", character.getId(), before, current.size());
            } else {
                log.debug("조인 변화 없음: characterId={}, size={}", character.getId(), before);
            }
        }
        log.info("캐릭터-성우 조인 upsert 결과: 저장 {}건", savedCount);
    }

    /**
     * 애니-성우 조인 upsert (집계 반영)
     */
    @Transactional
    public void upsertAnimeVoiceActorJoins(Long animeId, Set<Long> voiceActorIds) {
        if (animeId == null || voiceActorIds == null || voiceActorIds.isEmpty()) return;
        Anime anime = animeRepository.findById(animeId).orElse(null);
        if (anime == null) return;

        Set<VoiceActor> voices = new java.util.HashSet<>(voiceActorRepository.findAllById(voiceActorIds));
        if (voices.isEmpty()) return;

        java.util.Set<VoiceActor> current = anime.getVoiceActors() != null ? new java.util.HashSet<>(anime.getVoiceActors()) : new java.util.HashSet<>();
        int before = current.size();
        current.addAll(voices);
        if (current.size() > before) {
            anime.setVoiceActors(current);
            animeRepository.save(anime);
        }
    }
    
    /**
     * 성우 데이터만 처리 (비동기)
     */
    public void processVoiceActorsAsync(Long animeId, Long malId) {
        processVoiceActorsInBackground(animeId, malId);
    }
    
    /**
     * 디렉터 데이터만 처리 (비동기)
     * - 현재 Jikan API에 디렉터 정보가 없어 로그만 출력
     */
    public void processDirectorsAsync(Long animeId, Long malId) {
        processDirectorsInBackground(animeId, malId);
    }
    
    /**
     * 캐릭터 데이터만 처리 (비동기)
     * - Jikan API에서 캐릭터 정보를 조회하여 저장
     */
    public void processCharactersAsync(Long animeId, Long malId) {
        processCharactersInBackground(animeId, malId);
    }
    
    /**
     * 성우 데이터 백그라운드 처리
     */
    public void processVoiceActorsInBackground(Long animeId, Long malId) {
        // 새로운 쓰기 가능한 트랜잭션 생성
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        def.setReadOnly(false);
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        
        TransactionStatus status = transactionManager.getTransaction(def);
        
        try {
            // 저장된 애니메이션 조회
            Anime anime = animeRepository.findById(animeId).orElse(null);
            if (anime == null) {
                log.warn("애니메이션을 찾을 수 없음: ID {}", animeId);
                return;
            }
            
            // Jikan API에서 캐릭터/성우 정보 조회
            AnimeCharactersJikanDto charactersDto = jikanApiService.getAnimeCharacters(malId);
            if (charactersDto == null || charactersDto.getData() == null) {
                log.warn("캐릭터/성우 데이터 없음: MAL ID {}", malId);
                return;
            }
            
            // DTO를 Map으로 변환
            Map<String, Object> charactersData = convertCharactersToMap(charactersDto);
            
            // 성우 처리 - 마스터만 upsert (조인/애니 매핑 금지)
            Set<VoiceActor> voiceActors = dataMapper.mapToVoiceActors(charactersData);
            if (!voiceActors.isEmpty()) {
                Set<VoiceActor> managedVoiceActors = processVoiceActorsBatch(voiceActors);
                log.info("성우 마스터 upsert 완료: {}명 (MAL ID {})", managedVoiceActors.size(), malId);
            }
            
            // 애니메이션 업데이트
            animeRepository.save(anime);
            
            // 트랜잭션 커밋
            transactionManager.commit(status);
            
        } catch (Exception e) {
            log.error("성우 처리 실패: MAL ID {} - 재시도 예정", malId, e);
            transactionManager.rollback(status);
            retryVoiceActors(animeId, malId, 1);
        }
    }
    
    /**
     * 디렉터 데이터 백그라운드 처리
     */
    public void processDirectorsInBackground(Long animeId, Long malId) {
        // 새로운 쓰기 가능한 트랜잭션 생성
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        def.setReadOnly(false);
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        
        TransactionStatus status = transactionManager.getTransaction(def);
        
        try {
            // 저장된 애니메이션 조회
            Anime anime = animeRepository.findById(animeId).orElse(null);
            if (anime == null) {
                log.warn("애니메이션을 찾을 수 없음: ID {}", animeId);
                return;
            }
            
            // Jikan API에서 애니메이션 상세 정보 조회
            var details = jikanApiService.getAnimeDetails(malId);
            if (details == null) {
                log.warn("애니메이션 상세 데이터 없음: MAL ID {}", malId);
                return;
            }
            
            // DTO를 Map으로 변환 (현재는 사용하지 않음)
            // Map<String, Object> jikanData = convertToMap(details);
            
            // 디렉터 처리 (현재는 Jikan API에 디렉터 정보가 없으므로 로그만 출력)
            log.info("디렉터 데이터 처리: MAL ID {} (현재 Jikan API에 디렉터 정보 없음)", malId);
            
            // 애니메이션 업데이트
            animeRepository.save(anime);
            
            // 트랜잭션 커밋
            transactionManager.commit(status);
            
        } catch (Exception e) {
            log.error("디렉터 처리 실패: MAL ID {} - 재시도 예정", malId, e);
            transactionManager.rollback(status);
            retryDirectors(animeId, malId, 1);
        }
    }
    
    /**
     * 캐릭터 데이터 백그라운드 처리
     */
    public void processCharactersInBackground(Long animeId, Long malId) {
        // 새로운 쓰기 가능한 트랜잭션 생성
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        def.setReadOnly(false);
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        
        TransactionStatus status = transactionManager.getTransaction(def);
        
        try {
            // 저장된 애니메이션 조회
            Anime anime = animeRepository.findById(animeId).orElse(null);
            if (anime == null) {
                log.warn("애니메이션을 찾을 수 없음: ID {}", animeId);
                return;
            }
            
            // Jikan API에서 캐릭터/성우 정보 조회
            AnimeCharactersJikanDto charactersDto = jikanApiService.getAnimeCharacters(malId);
            if (charactersDto == null || charactersDto.getData() == null) {
                log.warn("캐릭터/성우 데이터 없음: MAL ID {}", malId);
                return;
            }
            
            // DTO를 Map으로 변환
            Map<String, Object> charactersData = convertCharactersToMap(charactersDto);
            
            // 캐릭터 처리 - 마스터만 upsert (조인/애니 매핑 금지)
            Set<Character> characters = dataMapper.mapToCharacters(charactersData);
            if (!characters.isEmpty()) {
                Set<Character> managedCharacters = processCharactersBatch(characters);
                log.info("캐릭터 마스터 upsert 완료: {}명 (MAL ID {})", managedCharacters.size(), malId);
            }
            
            // 애니메이션 업데이트 제거(조인 금지)
            
            // 트랜잭션 커밋
            transactionManager.commit(status);
            
        } catch (Exception e) {
            log.error("캐릭터 처리 실패: MAL ID {} - 재시도 예정", malId, e);
            transactionManager.rollback(status);
            retryCharacters(animeId, malId, 1);
        }
    }
    
    /**
     * 성우 처리 재시도 로직
     */
    private void retryVoiceActors(Long animeId, Long malId, int attempt) {
        if (attempt > 3) {
            log.error("성우 처리 최종 실패: MAL ID {} (재시도 3회 초과)", malId);
            return;
        }
        
        try {
            long delayMs = (long) Math.pow(2, attempt) * 1000;
            Thread.sleep(delayMs);
            
            log.info("성우 처리 재시도: MAL ID {} (시도 {}/3)", malId, attempt);
            processVoiceActorsInBackground(animeId, malId);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("성우 재시도 중 인터럽트: MAL ID {}", malId);
        } catch (Exception e) {
            log.error("성우 재시도 실패: MAL ID {} (시도 {}/3)", malId, attempt, e);
            retryVoiceActors(animeId, malId, attempt + 1);
        }
    }
    
    /**
     * 디렉터 처리 재시도 로직
     */
    private void retryDirectors(Long animeId, Long malId, int attempt) {
        if (attempt > 3) {
            log.error("디렉터 처리 최종 실패: MAL ID {} (재시도 3회 초과)", malId);
            return;
        }
        
        try {
            long delayMs = (long) Math.pow(2, attempt) * 1000;
            Thread.sleep(delayMs);
            
            log.info("디렉터 처리 재시도: MAL ID {} (시도 {}/3)", malId, attempt);
            processDirectorsInBackground(animeId, malId);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("디렉터 재시도 중 인터럽트: MAL ID {}", malId);
        } catch (Exception e) {
            log.error("디렉터 재시도 실패: MAL ID {} (시도 {}/3)", malId, attempt, e);
            retryDirectors(animeId, malId, attempt + 1);
        }
    }
    
    /**
     * 캐릭터 처리 재시도 로직
     */
    private void retryCharacters(Long animeId, Long malId, int attempt) {
        if (attempt > 3) {
            log.error("캐릭터 처리 최종 실패: MAL ID {} (재시도 3회 초과)", malId);
            return;
        }
        
        try {
            long delayMs = (long) Math.pow(2, attempt) * 1000;
            Thread.sleep(delayMs);
            
            log.info("캐릭터 처리 재시도: MAL ID {} (시도 {}/3)", malId, attempt);
            processCharactersInBackground(animeId, malId);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("캐릭터 재시도 중 인터럽트: MAL ID {}", malId);
        } catch (Exception e) {
            log.error("캐릭터 재시도 실패: MAL ID {} (시도 {}/3)", malId, attempt, e);
            retryCharacters(animeId, malId, attempt + 1);
        }
    }
}