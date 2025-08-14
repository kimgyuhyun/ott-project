package com.ottproject.ottbackend.dto;

import com.ottproject.ottbackend.enums.SkipType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 스킵 사용 로깅 요청 DTO
 * - 사용 타입과 시점을 전달
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SkipUsageRequestDto { // 스킵 사용 로깅 요청
    @NotNull
    private SkipType type; // INTRO/OUTRO
    @NotNull @Min(0)
    private Integer atSec; // 사용 시점(초)
}


