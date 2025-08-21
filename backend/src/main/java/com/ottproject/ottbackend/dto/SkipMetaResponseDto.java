package com.ottproject.ottbackend.dto;

import lombok.*;

/**
 * 스킵 메타 응답 DTO
 *
 * 큰 흐름
 * - 인트로/엔딩 구간을 초 단위로 전달한다.
 *
 * 필드 개요
 * - introStart/introEnd/outroStart/outroEnd
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