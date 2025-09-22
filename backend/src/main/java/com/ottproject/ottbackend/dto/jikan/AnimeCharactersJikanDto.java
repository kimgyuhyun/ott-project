package com.ottproject.ottbackend.dto.jikan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Jikan Anime 캐릭터/성우 응답 DTO (필요 필드만 최소화)
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnimeCharactersJikanDto {
    private List<CharacterItem> data;
    
    // ===== Getter 메서드 =====
    public List<CharacterItem> getData() { return data; }
    public void setData(List<CharacterItem> data) { this.data = data; }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CharacterItem {
        private CharacterRef character;
        private List<VoiceActorRef> voice_actors;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CharacterRef {
        private Long mal_id;
        private String name;
        private Images images;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VoiceActorRef {
        private String language;
        private Person person;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Person { private Long mal_id; private String name; }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Images { private Jpg jpg; }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Jpg { private String image_url; }
}


