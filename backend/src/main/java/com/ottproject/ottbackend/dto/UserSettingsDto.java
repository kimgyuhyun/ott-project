package com.ottproject.ottbackend.dto;

import lombok.*;

/**
 * 사용자 재생 설정 DTO
 *
 * 큰 흐름
 * - 자동 스킵/기본 화질/자동 다음 화/테마 설정을 노출한다.
 *
 * 필드 개요
 * - autoSkipIntro/autoSkipEnding/defaultQuality/autoNextEpisode/theme
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsDto { // 사용자 재생 설정
    private Boolean autoSkipIntro; // 자동 인트로 스킵
    private Boolean autoSkipEnding; // 자동 엔딩 스킵
    private String defaultQuality; // 기본 화질
    private Boolean autoNextEpisode; // 다음 화 자동재생
    private String theme; // 테마 설정 (light/dark)
}