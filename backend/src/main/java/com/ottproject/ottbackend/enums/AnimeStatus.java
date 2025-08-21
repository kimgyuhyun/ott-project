package com.ottproject.ottbackend.enums;

/**
 * 애니 방영 상태
 *
 * 큰 흐름
 * - 작품의 방영 라이프사이클 상태를 표현한다.
 *
 * 상수 개요
 * - ONGOING: 방영중
 * - COMPLETED: 완결
 * - UPCOMING: 방영예정
 * - HIATUS: 방영중단
 */
public enum AnimeStatus { // 애니 방영 상태 열거형 시작
    ONGOING("방영중"), // 방영중
    COMPLETED("완결"), // 완결
    UPCOMING("방영예정"), // 방영 예정
    HIATUS("방영중단"); // 방영 중단

    private final String description; // 한글 설명

    AnimeStatus(String description) { // 생성자
        this.description = description; // 설명 설정
    }

    public String getDescription() { // 설명 조회
        return description; // 값 반환
    }
}
