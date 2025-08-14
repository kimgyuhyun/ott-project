package com.ottproject.ottbackend.dto;

import lombok.*;

/**
 * 스킵 메타 응답 DTO
 * - 인트로/엔딩 구간(초)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkipMetaResponseDto { // 스킵 메타 응답
    private Integer introStart; private Integer introEnd; // 인트로
    private Integer outroStart; private Integer outroEnd; // 엔딩
}