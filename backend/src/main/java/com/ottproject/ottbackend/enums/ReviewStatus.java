package com.ottproject.ottbackend.enums;

/**
 * 리뷰 상태 열거형
 *
 * 큰 흐름
 * - 리뷰의 현재 상태를 표현한다.
 *
 * 상수 개요
 * - ACTIVE/DELETED/REPORTED
 */
public enum ReviewStatus { // 리뷰 상태
    ACTIVE("활성"), // 정상
    DELETED("삭제됨"), // 삭제됨
    REPORTED("신고됨"); // 신고됨

    private final String displayName; // 표시 이름

    ReviewStatus(String displayName) { // 생성자
        this.displayName = displayName; // 설정
    }

    public String getDisplayName() { // 표시 이름 조회
        return displayName; // 값 반환
    }
}
