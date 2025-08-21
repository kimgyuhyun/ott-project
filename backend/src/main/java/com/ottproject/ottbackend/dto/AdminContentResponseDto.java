package com.ottproject.ottbackend.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Admin 컨텐츠 응답 DTO
 *
 * 필드 개요
 * - id: 식별자
 * - type/locale/position/published: 노출 제어 메타데이터
 * - title/content/actionText/actionUrl: 표시 데이터
 * - createdAt/updatedAt: 생성/수정 시각
 */
@Getter // 접근자
@Builder // 빌더 생성
public class AdminContentResponseDto { // DTO 시작
    private Long id; // 식별자
    private String type; // 유형
    private String locale; // 언어 코드
    private Integer position; // 노출 순서
    private boolean published; // 공개 여부
    private String title; // 제목
    private String content; // 본문
    private String actionText; // CTA 텍스트
    private String actionUrl; // CTA URL
    private LocalDateTime createdAt; // 생성 시각
    private LocalDateTime updatedAt; // 수정 시각
}


