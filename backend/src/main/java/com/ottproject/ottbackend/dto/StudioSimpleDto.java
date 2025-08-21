package com.ottproject.ottbackend.dto;

import lombok.*;

/**
 * 제작사 간단 DTO
 *
 * 큰 흐름
 * - 제작사 배지/리스트에 필요한 최소 정보를 제공한다.
 *
 * 필드 개요
 * - id/name/logoUrl/country: 식별/명칭/로고/국가
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudioSimpleDto {
    private Long id; // 제작사 ID
    private String name; // 제작사명
    private String logoUrl; // 로고 URL
    private String country; // 제작국
}
