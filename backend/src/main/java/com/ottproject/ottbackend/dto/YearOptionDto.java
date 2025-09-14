package com.ottproject.ottbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 년도/분기 옵션 DTO
 * 필터링에서 사용할 년도별 및 분기별 옵션을 나타냄
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class YearOptionDto {
    private String value;    // 필터링에 사용할 값 (예: "2025", "2025-Q1")
    private String label;    // 화면에 표시할 라벨 (예: "2025년", "2025년 1분기")
    private String type;     // 옵션 타입 ("year", "quarter", "decade", "before")
}
