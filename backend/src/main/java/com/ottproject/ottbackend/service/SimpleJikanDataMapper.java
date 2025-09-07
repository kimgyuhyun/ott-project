package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.entity.Genre;
import com.ottproject.ottbackend.entity.Studio;
import com.ottproject.ottbackend.entity.Director;
import com.ottproject.ottbackend.entity.VoiceActor;
import com.ottproject.ottbackend.entity.Character;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.exception.AdultContentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 간단한 Jikan API 데이터 매핑 서비스 (DTO 없이)
 * 
 * 큰 흐름
 * - Jikan API Map 데이터를 Anime 엔티티로 변환한다.
 * - 19금 콘텐츠 필터링과 데이터 변환 로직을 처리한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimpleJikanDataMapper {
    
    /**
     * Jikan API Map 데이터를 Anime 엔티티로 변환
     */
    public Anime mapToAnime(Map<String, Object> jikanData) {
        // 19금 콘텐츠 체크
        String rating = (String) jikanData.get("rating");
        if (isAdultContent(rating)) {
            throw new AdultContentException("19금 콘텐츠는 제외됩니다: " + jikanData.get("title"));
        }
        
        // 기본 정보 추출
        Long malId = convertToLong(jikanData.get("mal_id"));
        String title = (String) jikanData.get("title");
        String titleEn = (String) jikanData.get("title_english");
        String titleJp = (String) jikanData.get("title_japanese");
        
        // 한국어 제목 조회 (없으면 null)
        String koreanTitle = getKoreanAnimeTitle(title, titleEn, titleJp);
        
        // 타이틀 우선순위: 한국어 > 일본어 > 영어 > 원제
        String displayTitle = determineDisplayTitle(koreanTitle, titleJp, titleEn, title);
        String synopsis = (String) jikanData.get("synopsis");
        
        // 한국어 시놉시스 조회 (없으면 null)
        String koreanSynopsis = getKoreanAnimeSynopsis(synopsis);
        Integer episodes = convertToInteger(jikanData.get("episodes"));
        String status = (String) jikanData.get("status");
        String type = (String) jikanData.get("type");
        String source = (String) jikanData.get("source");
        Integer duration = convertToInteger(jikanData.get("duration"));
        
        // 평점 정보 (안전한 변환)
        Double score = convertToDouble(jikanData.get("score"));
        Integer scoredBy = convertToInteger(jikanData.get("scored_by"));
        
        // 날짜 정보 (null 체크)
        Map<String, Object> aired = (Map<String, Object>) jikanData.get("aired");
        LocalDate releaseDate = null;
        LocalDate endDate = null;
        if (aired != null) {
            releaseDate = convertToLocalDate((String) aired.get("from"));
            endDate = convertToLocalDate((String) aired.get("to"));
        }
        
        // 방송 정보 (null 체크)
        Map<String, Object> broadcast = (Map<String, Object>) jikanData.get("broadcast");
        String broadcastDay = "미정";
        String broadcastTime = "미정";
        if (broadcast != null) {
            broadcastDay = convertDay((String) broadcast.get("day"));
            broadcastTime = (String) broadcast.get("time");
            if (broadcastTime == null) broadcastTime = "미정";
        }
        
        // 이미지 정보 (null 체크)
        Map<String, Object> images = (Map<String, Object>) jikanData.get("images");
        String posterUrl = "";
        if (images != null) {
            Map<String, Object> jpg = (Map<String, Object>) images.get("jpg");
            if (jpg != null) {
                posterUrl = (String) jpg.get("image_url");
                if (posterUrl == null) posterUrl = "";
            }
        }
        
        return Anime.createAnime(
            malId, // MyAnimeList ID
            displayTitle, // 우선순위에 따른 제목 (한국어 > 일본어 > 영어 > 원제)
            titleEn,
            titleJp,
            koreanSynopsis != null ? koreanSynopsis : (synopsis != null ? synopsis : ""), // 한국어 시놉시스 우선
            koreanSynopsis != null ? koreanSynopsis : (synopsis != null ? synopsis : ""), // 한국어 시놉시스 우선
            posterUrl, // null 허용
            episodes, // null 허용
            convertStatus(status),
            releaseDate, // null 허용
            endDate, // null 허용
            convertAgeRating(rating),
            score, // null 허용
            scoredBy, // null 허용
            false, // isExclusive
            isNewAnime(releaseDate),
            isPopularAnime(score, scoredBy),
            "Finished Airing".equals(status),
            true, // isSubtitle
            determineIsDub(score, scoredBy),
            determineIsSimulcast(releaseDate),
            broadcastDay, // null 허용
            broadcastTime, // null 허용
            extractQuarter(releaseDate), // season
            extractYear(releaseDate), // year
            type, // null 허용
            convertDuration(duration), // null 허용
            source, // null 허용
            "일본", // country
            "일본어", // language
            extractQuarter(releaseDate), // releaseQuarter
            episodes != null ? episodes : 0 // currentEpisodes
        );
    }
    
    /**
     * 타이틀 우선순위 결정: 한국어 > 일본어 > 영어 > 원제
     */
    private String determineDisplayTitle(String koreanTitle, String titleJp, String titleEn, String title) {
        if (koreanTitle != null && !koreanTitle.trim().isEmpty()) {
            return koreanTitle;
        }
        if (titleJp != null && !titleJp.trim().isEmpty()) {
            return titleJp;
        }
        if (titleEn != null && !titleEn.trim().isEmpty()) {
            return titleEn;
        }
        if (title != null && !title.trim().isEmpty()) {
            return title;
        }
        return null;
    }
    
    /**
     * 19금 콘텐츠 체크
     */
    private boolean isAdultContent(String rating) {
        if (rating == null) return false;
        return rating.contains("R+") || rating.contains("Rx") || rating.contains("R - 17+");
    }
    
    /**
     * 방영 상태 변환
     */
    private AnimeStatus convertStatus(String status) {
        if (status == null) return AnimeStatus.UPCOMING;
        
        switch (status) {
            case "Currently Airing": return AnimeStatus.ONGOING;
            case "Finished Airing": return AnimeStatus.COMPLETED;
            case "Not yet aired": return AnimeStatus.UPCOMING;
            default: return AnimeStatus.HIATUS;
        }
    }
    
    /**
     * 연령 등급 변환
     */
    private String convertAgeRating(String rating) {
        if (rating == null || rating.isEmpty()) return "전체 이용가";
        
        if (rating.contains("G") || rating.contains("PG")) {
            return "전체 이용가";
        } else if (rating.contains("PG-13")) {
            return "12세이상";
        } else if (rating.contains("R - 17+")) {
            return "15세이상";
        }
        
        return "전체 이용가";
    }
    
    /**
     * 날짜 문자열을 LocalDate로 변환
     */
    private LocalDate convertToLocalDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) return null;
        
        try {
            String datePart = dateString.split("T")[0];
            return LocalDate.parse(datePart);
        } catch (DateTimeParseException e) {
            log.warn("날짜 변환 실패: {}", dateString, e);
            return null;
        }
    }
    
    /**
     * 러닝타임을 분 단위로 변환 (초 → 분)
     */
    private Integer convertDuration(Integer durationSeconds) {
        if (durationSeconds == null || durationSeconds <= 0) return null; // null 허용
        return Math.max(1, durationSeconds / 60); // 최소 1분
    }
    
    /**
     * 요일 변환 (영어 → 한글)
     */
    private String convertDay(String day) {
        if (day == null) return "미정";
        
        switch (day.toLowerCase()) {
            case "monday": return "월";
            case "tuesday": return "화";
            case "wednesday": return "수";
            case "thursday": return "목";
            case "friday": return "금";
            case "saturday": return "토";
            case "sunday": return "일";
            default: return "미정";
        }
    }
    
    /**
     * 연도 추출
     */
    private Integer extractYear(LocalDate releaseDate) {
        if (releaseDate == null) return null; // null 허용
        return releaseDate.getYear();
    }
    
    /**
     * 분기 추출 (1분기, 2분기, 3분기, 4분기)
     */
    private String extractQuarter(LocalDate releaseDate) {
        if (releaseDate == null) return null; // null 허용
        
        int year = releaseDate.getYear();
        int month = releaseDate.getMonthValue();
        
        if (month >= 1 && month <= 3) {
            return year + "년 1분기";
        } else if (month >= 4 && month <= 6) {
            return year + "년 2분기";
        } else if (month >= 7 && month <= 9) {
            return year + "년 3분기";
        } else {
            return year + "년 4분기";
        }
    }
    
    /**
     * 신작 여부 판단 (1년 이내)
     */
    private Boolean isNewAnime(LocalDate releaseDate) {
        if (releaseDate == null) return false;
        return releaseDate.isAfter(LocalDate.now().minusYears(1));
    }
    
    /**
     * 인기작 여부 판단 (평점 8.0 이상, 투표수 10만 이상)
     */
    private Boolean isPopularAnime(Double rating, Integer ratingCount) {
        if (rating == null || ratingCount == null || rating <= 0 || ratingCount <= 0) return false;
        return rating >= 8.0 && ratingCount >= 100000;
    }
    
    /**
     * 더빙 여부 판단 (인기작만 더빙 제공)
     */
    private Boolean determineIsDub(Double rating, Integer ratingCount) {
        if (rating == null || ratingCount == null) return false;
        return isPopularAnime(rating, ratingCount);
    }
    
    /**
     * 동시방영 여부 판단 (2020년 이후 작품)
     */
    private Boolean determineIsSimulcast(LocalDate releaseDate) {
        if (releaseDate == null) return false;
        return releaseDate.isAfter(LocalDate.of(2020, 1, 1));
    }
    
    /**
     * 장르 목록을 Genre 엔티티 Set으로 변환
     */
    public Set<Genre> mapToGenres(List<Map<String, Object>> jikanGenres) {
        Set<Genre> genres = new HashSet<>();
        
        if (jikanGenres != null) {
            for (Map<String, Object> jikanGenre : jikanGenres) {
                if (jikanGenre != null) {
                    String name = (String) jikanGenre.get("name");
                    if (name != null && !name.trim().isEmpty()) {
                        try {
                            Genre genre = Genre.createGenre(name.trim(), "", generateConsistentColor(name));
                            genres.add(genre);
                        } catch (Exception e) {
                            log.warn("장르 생성 실패: {}", name, e);
                        }
                    }
                }
            }
        }
        
        return genres;
    }
    
    /**
     * 스튜디오 목록을 Studio 엔티티 Set으로 변환
     */
    public Set<Studio> mapToStudios(List<Map<String, Object>> jikanStudios) {
        Set<Studio> studios = new HashSet<>();
        
        if (jikanStudios != null) {
            for (Map<String, Object> jikanStudio : jikanStudios) {
                if (jikanStudio != null) {
                    String name = (String) jikanStudio.get("name");
                    if (name != null && !name.trim().isEmpty()) {
                        try {
                            Studio studio = Studio.createStudio(name.trim(), name.trim(), name.trim(), "", "", "", "일본");
                            studios.add(studio);
                        } catch (Exception e) {
                            log.warn("스튜디오 생성 실패: {}", name, e);
                        }
                    }
                }
            }
        }
        
        return studios;
    }
    
    /**
     * 감독 목록을 Director 엔티티 Set으로 변환
     */
    @SuppressWarnings("unchecked")
    public Set<Director> mapToDirectors(Map<String, Object> jikanData) {
        Set<Director> directors = new HashSet<>();
        
        try {
            List<Map<String, Object>> staff = (List<Map<String, Object>>) jikanData.get("staff");
            if (staff != null) {
                for (Map<String, Object> staffMember : staff) {
                    if (staffMember != null) {
                        List<String> positions = (List<String>) staffMember.get("positions");
                        if (positions != null && positions.contains("Director")) {
                            String name = (String) staffMember.get("name");
                            if (name != null && !name.trim().isEmpty()) {
                                try {
                                    Director director = Director.createDirector(name.trim(), name.trim(), name.trim(), "", "");
                                    directors.add(director);
                                } catch (Exception e) {
                                    log.warn("감독 생성 실패: {}", name, e);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("감독 정보 추출 실패", e);
        }
        
        return directors;
    }
    
    /**
     * 성우 목록을 VoiceActor 엔티티 Set으로 변환
     */
    @SuppressWarnings("unchecked")
    public Set<VoiceActor> mapToVoiceActors(Map<String, Object> charactersData) {
        Set<VoiceActor> voiceActors = new HashSet<>();
        
        try {
            List<Map<String, Object>> characters = (List<Map<String, Object>>) charactersData.get("characters");
            if (characters != null) {
                for (Map<String, Object> character : characters) {
                    if (character != null) {
                        List<Map<String, Object>> voiceActorsList = (List<Map<String, Object>>) character.get("voice_actors");
                        if (voiceActorsList != null) {
                            for (Map<String, Object> voiceActor : voiceActorsList) {
                                if (voiceActor != null) {
                                    String language = (String) voiceActor.get("language");
                                    if ("Japanese".equals(language)) {
                                        Map<String, Object> person = (Map<String, Object>) voiceActor.get("person");
                                        if (person != null) {
                                            String name = (String) person.get("name");
                                            if (name != null && !name.trim().isEmpty()) {
                                                try {
                                                    // 중복 체크 후 생성
                                                    String koreanName = getKoreanCharacterName(name.trim());
                                                    VoiceActor voiceActorEntity = VoiceActor.createVoiceActor(
                                                        koreanName != null ? koreanName : name.trim(), // 한국어 우선
                                                        name.trim(), // 영어
                                                        name.trim(), // 일본어
                                                        "", ""
                                                    );
                                                    voiceActors.add(voiceActorEntity);
                                                } catch (Exception e) {
                                                    log.warn("성우 생성 실패: {}", name, e);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("성우 정보 추출 실패", e);
        }
        
        return voiceActors;
    }
    
    /**
     * 캐릭터 목록을 Character 엔티티 Set으로 변환
     */
    @SuppressWarnings("unchecked")
    public Set<Character> mapToCharacters(Map<String, Object> charactersData) {
        Set<Character> characters = new HashSet<>();
        
        try {
            List<Map<String, Object>> charactersList = (List<Map<String, Object>>) charactersData.get("characters");
            if (charactersList != null) {
                for (Map<String, Object> characterData : charactersList) {
                    if (characterData != null) {
                        Map<String, Object> character = (Map<String, Object>) characterData.get("character");
                        if (character != null) {
                            String name = (String) character.get("name");
                            if (name != null && !name.trim().isEmpty()) {
                                try {
                                    Map<String, Object> images = (Map<String, Object>) character.get("images");
                                    String imageUrl = "";
                                    if (images != null) {
                                        Map<String, Object> jpg = (Map<String, Object>) images.get("jpg");
                                        if (jpg != null) {
                                            imageUrl = (String) jpg.get("image_url");
                                            if (imageUrl == null) imageUrl = "";
                                        }
                                    }
                                    
                                    // 한국어 이름 조회 (없으면 null)
                                    String koreanName = getKoreanCharacterName(name.trim());
                                    
                                    // 캐릭터 이름이 null이면 건너뛰기
                                    if (name == null || name.trim().isEmpty()) {
                                        continue;
                                    }
                                    
                                    Character characterEntity = Character.createCharacter(
                                        koreanName != null ? koreanName : name.trim(),  // 한글 (한국어 없으면 원본)
                                        name.trim(),       // 영어 (원본)
                                        name.trim(),       // 일본어 (원본)
                                        imageUrl, 
                                        ""
                                    );
                                    characters.add(characterEntity);
                                } catch (Exception e) {
                                    log.warn("캐릭터 생성 실패: {}", name, e);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("캐릭터 정보 추출 실패", e);
        }
        
        return characters;
    }
    
    /**
     * 한국어 애니메 제목 조회
     * 
     * @param title 원제
     * @param titleEn 영어 제목
     * @param titleJp 일본어 제목
     * @return 한국어 제목 (없으면 null)
     */
    private String getKoreanAnimeTitle(String title, String titleEn, String titleJp) {
        // TODO: 향후 한국어 매핑 로직 구현
        // 1차: TMDB API에서 한국어 제목 조회
        // 2차: 수동 매핑 테이블에서 조회
        // 3차: null 반환 (한국어 없음)
        
        // 현재는 null 반환 (한국어 제목 없음)
        return null;
    }
    
    /**
     * 한국어 애니메 시놉시스 조회
     * 
     * @param originalSynopsis 원본 시놉시스 (영어/일본어)
     * @return 한국어 시놉시스 (없으면 null)
     */
    private String getKoreanAnimeSynopsis(String originalSynopsis) {
        if (originalSynopsis == null || originalSynopsis.trim().isEmpty()) {
            return null;
        }
        
        // TODO: 향후 한국어 매핑 로직 구현
        // 1차: TMDB API에서 한국어 시놉시스 조회
        // 2차: 수동 매핑 테이블에서 조회
        // 3차: null 반환 (한국어 없음)
        
        // 현재는 null 반환 (한국어 시놉시스 없음)
        return null;
    }
    
    /**
     * 한국어 캐릭터 이름 조회
     * 
     * @param originalName 원본 캐릭터 이름 (영어/일본어)
     * @return 한국어 이름 (없으면 null)
     */
    private String getKoreanCharacterName(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return null;
        }
        
        // TODO: 향후 한국어 매핑 로직 구현
        // 1차: TMDB API에서 한국어 이름 조회
        // 2차: 수동 매핑 테이블에서 조회
        // 3차: null 반환 (한국어 없음)
        
        // 현재는 null 반환 (한국어 이름 없음)
        return null;
    }
    
    /**
     * 안전한 Integer 변환 (String 또는 Number 모두 처리)
     */
    private Integer convertToInteger(Object value) {
        if (value == null) return null;
        
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                log.warn("숫자 변환 실패: {}", value);
                return null;
            }
        }
        
        return null;
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
     * 안전한 Long 변환 (String 또는 Number 모두 처리)
     */
    private Long convertToLong(Object value) {
        if (value == null) return null;
        
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                log.warn("Long 변환 실패: {}", value);
                return null;
            }
        }
        
        return null;
    }
    
    /**
     * 안전한 Double 변환 (String 또는 Number 모두 처리)
     */
    private Double convertToDouble(Object value) {
        if (value == null) return 0.0;
        
        if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                log.warn("숫자 변환 실패: {}", value);
                return 0.0;
            }
        }
        
        return 0.0;
    }
    
}
