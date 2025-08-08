package com.ottproject.ottbackend.dto;

import lombok.*;

/**
 * 장르 뱃지 DTO
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenreSimpleDto {
    private Long id; // 장르 ID
    private String name; // 장르명
    private String color; // UI 색상 코드
}
