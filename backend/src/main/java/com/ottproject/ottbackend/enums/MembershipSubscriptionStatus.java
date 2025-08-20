package com.ottproject.ottbackend.enums;

/**
 * 구독 상태
 * - ACTIVE: 활성
 * - PAST_DUE: 결제 연체(재시도 대기)
 * - CANCELED: 해지
 * - EXPIRED: 만료
 */
public enum MembershipSubscriptionStatus {
    ACTIVE,
    PAST_DUE,
    CANCELED,
    EXPIRED
}


