package com.ottproject.ottbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 자막 정보 DTO
 *
 * 큰 흐름
 * - 에피소드별 자막 정보를 전달한다.
 * - 다국어 자막 지원을 위한 언어 코드 포함
 *
 * 필드 개요
 * - id: 자막 ID
 * - episodeId: 에피소드 ID
 * - language: 언어 코드 (ko, en, ja)
 * - url: 웹VTT 파일 URL
 * - isDefault: 기본 자막 여부
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubtitleDto {
    private Long id;
    private Long episodeId;
    private String language; // 언어 코드 (ko, en, ja)
    private String url; // 웹VTT 파일 URL
    private boolean isDefault; // 기본 자막 여부
}
