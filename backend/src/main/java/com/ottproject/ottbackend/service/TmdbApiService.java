package com.ottproject.ottbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * TMDB API 서비스
 * 
 * 큰 흐름
 * - TMDB API를 통해 애니메이션의 한국어 정보를 조회한다.
 * - 제목, 시놉시스, 배경이미지 정보를 제공한다.
 * 
 * 필드 개요
 * - apiKey/baseUrl: TMDB API 설정
 * - restTemplate: HTTP 통신
 * - objectMapper: JSON 파싱
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TmdbApiService {
    
    @Value("${tmdb.api.key}")
    private String apiKey;
    
    @Value("${tmdb.api.base-url}")
    private String baseUrl;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * 애니메이션 검색 (한국어 정보 포함)
     * 
     * @param title 검색할 애니메이션 제목
     * @return TMDB 애니메이션 데이터 (한국어 정보 포함)
     */
    public TmdbAnimeData searchAnime(String title) {
        if (title == null || title.trim().isEmpty()) {
            log.warn("검색 제목이 비어있음");
            return null;
        }
        
        try {
            // TMDB API 검색 요청
            String searchUrl = baseUrl + "/search/movie" +
                "?api_key=" + apiKey +
                "&query=" + title.trim() +
                "&language=ko-KR" +
                "&include_adult=false";
            
            log.info("TMDB API 검색: {}", searchUrl);
            
            String response = restTemplate.getForObject(searchUrl, String.class);
            if (response == null) {
                log.warn("TMDB API 응답이 null: {}", title);
                return null;
            }
            
            // JSON 파싱
            Map<String, Object> jsonResponse = objectMapper.readValue(response, Map.class);
            Object resultsObj = jsonResponse.get("results");
            
            if (resultsObj == null) {
                log.warn("TMDB 검색 결과가 없음: {}", title);
                return null;
            }
            
            // 첫 번째 결과 사용 (가장 관련성 높은 결과)
            if (resultsObj instanceof java.util.List) {
                java.util.List<?> results = (java.util.List<?>) resultsObj;
                if (results.isEmpty()) {
                    log.warn("TMDB 검색 결과가 비어있음: {}", title);
                    return null;
                }
                
                Map<String, Object> firstResult = (Map<String, Object>) results.get(0);
                return parseTmdbAnimeData(firstResult);
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("TMDB API 검색 실패: {}", title, e);
            return null;
        }
    }
    
    /**
     * TMDB 애니메이션 데이터 파싱
     */
    private TmdbAnimeData parseTmdbAnimeData(Map<String, Object> data) {
        try {
            TmdbAnimeData animeData = new TmdbAnimeData();
            
            // 기본 정보
            animeData.setId((Integer) data.get("id"));
            animeData.setTitle((String) data.get("title"));
            animeData.setOriginalTitle((String) data.get("original_title"));
            animeData.setOverview((String) data.get("overview"));
            animeData.setReleaseDate((String) data.get("release_date"));
            animeData.setVoteAverage((Double) data.get("vote_average"));
            animeData.setPopularity((Double) data.get("popularity"));
            
            // 이미지 정보
            String posterPath = (String) data.get("poster_path");
            if (posterPath != null) {
                animeData.setPosterUrl("https://image.tmdb.org/t/p/w500" + posterPath);
            }
            
            String backdropPath = (String) data.get("backdrop_path");
            if (backdropPath != null) {
                animeData.setBackdropUrl("https://image.tmdb.org/t/p/w1280" + backdropPath);
            }
            
            // 한국어 정보 확인
            boolean hasKoreanData = animeData.getTitle() != null && 
                                  !animeData.getTitle().trim().isEmpty() &&
                                  animeData.getOverview() != null && 
                                  !animeData.getOverview().trim().isEmpty();
            
            animeData.setHasKoreanData(hasKoreanData);
            
            log.info("TMDB 데이터 파싱 완료: {} (한국어: {})", 
                animeData.getTitle(), hasKoreanData);
            
            return animeData;
            
        } catch (Exception e) {
            log.error("TMDB 데이터 파싱 실패", e);
            return null;
        }
    }
    
    /**
     * TMDB 애니메이션 데이터 DTO
     */
    public static class TmdbAnimeData {
        private Integer id;
        private String title;           // 한국어 제목
        private String originalTitle;   // 원어 제목
        private String overview;        // 한국어 시놉시스
        private String releaseDate;
        private Double voteAverage;
        private Double popularity;
        private String posterUrl;       // 포스터 이미지
        private String backdropUrl;     // 배경 이미지
        private boolean hasKoreanData;  // 한국어 데이터 여부
        
        // Getters and Setters
        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getOriginalTitle() { return originalTitle; }
        public void setOriginalTitle(String originalTitle) { this.originalTitle = originalTitle; }
        
        public String getOverview() { return overview; }
        public void setOverview(String overview) { this.overview = overview; }
        
        public String getReleaseDate() { return releaseDate; }
        public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }
        
        public Double getVoteAverage() { return voteAverage; }
        public void setVoteAverage(Double voteAverage) { this.voteAverage = voteAverage; }
        
        public Double getPopularity() { return popularity; }
        public void setPopularity(Double popularity) { this.popularity = popularity; }
        
        public String getPosterUrl() { return posterUrl; }
        public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }
        
        public String getBackdropUrl() { return backdropUrl; }
        public void setBackdropUrl(String backdropUrl) { this.backdropUrl = backdropUrl; }
        
        public boolean isHasKoreanData() { return hasKoreanData; }
        public void setHasKoreanData(boolean hasKoreanData) { this.hasKoreanData = hasKoreanData; }
    }
}
