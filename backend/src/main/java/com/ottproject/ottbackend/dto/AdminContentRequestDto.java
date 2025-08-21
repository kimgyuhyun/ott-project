package com.ottproject.ottbackend.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Admin 컨텐츠 생성/수정 요청 DTO
 *
 * 필드 개요
 * - type: 유형(FAQ|BENEFIT|CTA)
 * - locale: 언어 코드(ko|en)
 * - position: 노출 순서
 * - published: 공개 여부
 * - title: 제목/질문/타이틀
 * - content: 본문/답변/설명
 * - actionText: CTA 버튼 텍스트(옵션)
 * - actionUrl: CTA 버튼 URL(옵션)
 */
@Getter // 접근자 생성
@Setter // 설정자 생성
public class AdminContentRequestDto { // DTO 시작
    private String type; // 유형
    private String locale; // 언어 코드
    private Integer position; // 노출 순서
    private Boolean published; // 공개 여부
    private String title; // 제목
    private String content; // 본문
    private String actionText; // CTA 텍스트
    private String actionUrl; // CTA URL
}


