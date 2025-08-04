package com.ottproject.ottbackend.enums;

/**
 * 인증 제공자를 정의하는 열거형
 * LOCAL: 자체 로그인
 * GOOGLE: 구글 소셜 로그인
 * NAVER: 네이버 소셜 로그인
 * KAKAO: 카카오 소셜 로그인
 */
public enum AuthProvider {
    LOCAL,  // 자체 로그인
    GOOGLE, // 구글 소셜 로그인
    NAVER,  // 네이버 소셜 로그인
    KAKAO   // 카카오 소셜 로그인
}