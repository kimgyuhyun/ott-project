package com.ottproject.ottbackend.enums;

/**
 * 인증 제공자 열거형
 *
 * 큰 흐름
 * - 사용자 계정의 인증 소스를 구분한다.
 *
 * 상수 개요
 * - LOCAL/GOOGLE/NAVER/KAKAO
 */
public enum AuthProvider { // 인증 제공자
    LOCAL,  // 자체 로그인
    GOOGLE, // 구글 소셜 로그인
    NAVER,  // 네이버 소셜 로그인
    KAKAO   // 카카오 소셜 로그인
}