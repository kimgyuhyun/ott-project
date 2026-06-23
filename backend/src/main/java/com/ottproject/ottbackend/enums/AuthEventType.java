package com.ottproject.ottbackend.enums;

/**
 * 인증 이벤트 유형 열거형
 *
 * 큰 흐름
 * - 로그인/로그아웃 등 인증 관련 행위를 감사(audit) 기록하기 위한 이벤트 종류를 구분한다.
 * - 통계 스냅샷(DailyStats) 집계의 원천 데이터 분류 기준으로 사용된다.
 *
 * 상수 개요
 * - LOGIN_SUCCESS: 로그인 성공(이메일/소셜)
 * - LOGIN_FAIL: 로그인 실패(잘못된 비밀번호/존재하지 않는 계정/소셜 인증 실패/계정 잠금 등)
 * - LOGOUT: 명시적 로그아웃(세션 무효화)
 * - SESSION_EXPIRED: 세션 타임아웃/브라우저 종료 등으로 인한 비명시적 세션 종료
 * - WITHDRAW: 회원 탈퇴
 */
public enum AuthEventType { // 인증 이벤트 유형
    LOGIN_SUCCESS,   // 로그인 성공
    LOGIN_FAIL,      // 로그인 실패
    LOGOUT,          // 명시적 로그아웃
    SESSION_EXPIRED, // 세션 만료(타임아웃/창 닫음 등 비명시적 종료)
    WITHDRAW         // 회원 탈퇴
}
