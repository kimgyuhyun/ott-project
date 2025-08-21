package com.ottproject.ottbackend.dto; // DTO 패키지 선언

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 자동완성(제목만) 응답 DTO
 *
 * 큰 흐름
 * - 자동완성 리스트에서 각 제목 한 건만 표현한다.
 *
 * 필드 개요
 * - title: 자동완성 표시 문자열
 */
@Data
@Builder
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
public class SearchSuggestTitleDto {
    private String title; // 자동완성에서 반환할 제목 필드
}