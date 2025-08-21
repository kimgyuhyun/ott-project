package com.ottproject.ottbackend.enums;

/**
 * 사용자 권한 열거형
 *
 * 큰 흐름
 * - 접근 제어에서 사용하는 역할을 구분한다.
 *
 * 상수 개요
 * - USER/ADMIN
 */
public enum UserRole { // 역할
    USER,   // 일반 사용자
    ADMIN   // 관리자
}