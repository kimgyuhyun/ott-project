package com.ottproject.ottbackend.dto;

import lombok.*;

/**
 * 제작사 간단 DTO
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
