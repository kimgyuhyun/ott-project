package com.ottproject.ottbackend.dto.jikan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Jikan Anime 상세 응답 DTO (필요 필드만 최소화)
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnimeDetailsJikanDto {
    private Data data;
    
    // ===== Getter 메서드 =====
    public Data getData() { return data; }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String title;
        private String title_english;
        private String title_japanese;
        private String synopsis;
        private Integer episodes;
        private String status;
        private String type;
        private String source;
        private String duration; // "24 min per ep" 형태의 문자열로 받음
        private Double score;
        private Integer scored_by;
        private Aired aired;
        private Broadcast broadcast;
        private Images images;
        private String rating;
        private List<NameOnly> genres;
        private List<NameOnly> studios;
        private List<NameOnly> themes;
        private List<NameOnly> demographics;
        private List<Staff> staff; // 옵션
        
        // ===== Getter 메서드 =====
        public String getTitle() { return title; }
        public String getTitle_english() { return title_english; }
        public String getTitle_japanese() { return title_japanese; }
        public String getSynopsis() { return synopsis; }
        public Integer getEpisodes() { return episodes; }
        public String getStatus() { return status; }
        public String getType() { return type; }
        public String getSource() { return source; }
        public String getDuration() { return duration; }
        public Double getScore() { return score; }
        public Integer getScored_by() { return scored_by; }
        public Aired getAired() { return aired; }
        public Broadcast getBroadcast() { return broadcast; }
        public Images getImages() { return images; }
        public String getRating() { return rating; }
        public List<NameOnly> getGenres() { return genres; }
        public List<NameOnly> getStudios() { return studios; }
        public List<NameOnly> getThemes() { return themes; }
        public List<NameOnly> getDemographics() { return demographics; }
        public List<Staff> getStaff() { return staff; }
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Aired { private String from; private String to; }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Broadcast { private String day; private String time; }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Images { private Jpg jpg; }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Jpg { private String image_url; }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NameOnly { private String name; }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Staff {
        private String name;
        private List<String> positions;
    }
}


