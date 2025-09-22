package com.ottproject.ottbackend.dto.jikan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnimeStaffJikanDto {
    private List<StaffItem> data;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StaffItem {
        private Person person;
        private List<String> positions;
        
        @Getter
        @Setter
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Person {
            private Long mal_id;
            private String name;
        }
        
        // 편의 메서드: person.name을 직접 접근
        public String getName() {
            return person != null ? person.getName() : null;
        }

        public Long getMalId() {
            return person != null ? person.getMal_id() : null;
        }
    }
}


