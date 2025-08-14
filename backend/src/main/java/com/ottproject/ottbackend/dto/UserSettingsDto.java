package com.ottproject.ottbackend.dto;

import lombok.*;

/**
 * 사용자 재생 설정 DTO
 * - 자동 스킵/기본 화질/자동 다음 화
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
}