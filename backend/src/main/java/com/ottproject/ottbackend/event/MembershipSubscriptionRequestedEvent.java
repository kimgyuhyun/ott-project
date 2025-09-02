package com.ottproject.ottbackend.event;

import lombok.Data;

/**
 * 멤버십 구독 요청 이벤트
 * 
 * 큰 흐름
 * - 결제 성공 시 멤버십 구독 생성을 요청하는 이벤트
 * 
 * 필드 개요
 * - userId: 사용자 ID
 * - planCode: 플랜 코드
 */
@Data
public class MembershipSubscriptionRequestedEvent {
    private final Long userId;
    private final String planCode;
    
    public MembershipSubscriptionRequestedEvent(Long userId, String planCode) {
        this.userId = userId;
        this.planCode = planCode;
    }
}
