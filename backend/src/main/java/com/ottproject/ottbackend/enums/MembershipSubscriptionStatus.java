package com.ottproject.ottbackend.enums;

/**
 * 구독 상태 열거형
 *
 * 큰 흐름
 * - 구독의 현재 상태를 표현한다.
 *
 * 상수 개요
 * - ACTIVE/PAST_DUE/CANCELED/EXPIRED
 */
public enum MembershipSubscriptionStatus { // 구독 상태
    ACTIVE,     // 활성
    PAST_DUE,   // 연체(재시도 대기)
    CANCELED,   // 해지
    EXPIRED     // 만료
}


