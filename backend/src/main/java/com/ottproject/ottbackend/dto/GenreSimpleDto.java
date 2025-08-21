package com.ottproject.ottbackend.dto;

import lombok.*;

/**
 * 장르 뱃지 DTO
 *
 * 큰 흐름
 * - 장르 배지 표시를 위한 최소 데이터를 제공한다.
 *
 * 필드 개요
 * - id/name/color: 식별/명칭/색상
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
