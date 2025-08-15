package com.ottproject.ottbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 태그 간단 표현 DTO
 * - 목록/상세 화면에서 태그 배지 노출용 최소 필드 집합
 * - id/name/color 만 포함하여 네트워크 페이로드를 최소화
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


