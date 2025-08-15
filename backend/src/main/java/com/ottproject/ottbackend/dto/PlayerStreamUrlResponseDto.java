package com.ottproject.ottbackend.dto;

import lombok.*;

/**
 * 스트림 URL 응답 DTO
 * - secure_link 파라미터 포함 m3u8 URL
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerStreamUrlResponseDto { // 스트림 URL 응답
    private String url; // 서명된 master.m3u8 URL
}