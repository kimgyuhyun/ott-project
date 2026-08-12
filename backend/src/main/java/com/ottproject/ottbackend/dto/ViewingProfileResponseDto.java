package com.ottproject.ottbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 시청 프로필 응답
 *
 * 큰 흐름
 * - /api/profiles 계열 응답 본문이다. 선택 화면이 쓰는 값만 담는다.
 *
 * 필드 개요
 * - id: 프로필 식별자(선택 요청에 그대로 쓴다)
 * - name: 표시 이름
 */
@Getter
@Builder
@AllArgsConstructor
public class ViewingProfileResponseDto {

    private final Long id;
    private final String name;
}
