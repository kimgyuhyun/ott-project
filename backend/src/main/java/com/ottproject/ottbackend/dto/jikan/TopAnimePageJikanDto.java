package com.ottproject.ottbackend.dto.jikan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Jikan Top Anime 페이지 응답 DTO (필요 필드만 최소화)
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TopAnimePageJikanDto {
    private List<AnimeSummary> data;

    // ===== Getter 메서드 =====
    public List<AnimeSummary> getData() {
        return data;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnimeSummary {
        private Long mal_id;

        // ===== Getter 메서드 =====
        public Long getMal_id() {
            return mal_id;
        }
    }
}
