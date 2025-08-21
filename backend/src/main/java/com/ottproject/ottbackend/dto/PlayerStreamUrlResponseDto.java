package com.ottproject.ottbackend.dto;

import lombok.*;

/**
 * 스트림 URL 응답 DTO
 *
 * 큰 흐름
 * - Nginx secure_link 서명 파라미터가 포함된 m3u8 URL 을 전달한다.
 *
 * 필드 개요
 * - url: 서명된 master.m3u8 URL
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerStreamUrlResponseDto { // 스트림 URL 응답
    private String url; // 서명된 master.m3u8 URL
}