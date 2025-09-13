package com.ottproject.ottbackend.dto;

import lombok.*;

/**
 * 상태 옵션 DTO
 *
 * 큰 흐름
 * - 필터 옵션 표시를 위한 최소 데이터를 제공한다.
 *
 * 필드 개요
 * - key/label: 식별/명칭
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusOptionDto {
    private String key; // ONGOING, COMPLETED, etc.
    private String label; // 방영중, 완결, etc.
}
