package com.ottproject.ottbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 태그 간단 표현 DTO
 *
 * 큰 흐름
 * - 목록/상세 화면에서 태그 배지 노출용 최소 필드를 전달한다.
 * - id/name/color 로 페이로드를 최소화한다.
 *
 * 필드 개요
 * - id/name/color: 식별/명칭/색상
 */
@Data
@Builder
@NoArgsConstructor // 파라미터 없는 생성자
@AllArgsConstructor // 모든 필드를 받는 생성자
public class TagSimpleDto { // 태그 표시용 단순 DTO
    private Long id; // 태그 PK
    private String name; // 태그 이름(예: 가족, 감동)
    private String color; // 태그 색상(선택)
}


