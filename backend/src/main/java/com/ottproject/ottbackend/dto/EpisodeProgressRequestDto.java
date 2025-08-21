package com.ottproject.ottbackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 진행률 저장 요청 DTO
 *
 * 큰 흐름
 * - 현재 위치/총 길이를 전달해 진행률을 저장한다.
 * - Bean Validation 으로 0 이상 정수를 강제한다.
 *
 * 필드 개요
 * - positionSec/durationSec: 현재 위치/총 길이(초)
 */
@Getter // 게터 생성
@Setter // 세터 생성
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 모든 필드 생성자
public class EpisodeProgressRequestDto { // 진행률 저장 요청 DTO
    @NotNull @Min(0)
    private Integer positionSec; // 현재 위치(초)
    @NotNull @Min(0)
    private Integer durationSec; // 총 길이(초)
}


