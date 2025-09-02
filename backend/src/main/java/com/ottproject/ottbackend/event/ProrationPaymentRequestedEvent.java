package com.ottproject.ottbackend.event;

import lombok.Data;

/**
 * 차액 결제 요청 이벤트
 * 
 * 큰 흐름
 * - 멤버십 업그레이드 시 차액 결제를 요청하는 이벤트
 * 
 * 필드 개요
 * - userId: 사용자 ID
 * - amount: 차액 금액
 */
@Data
public class ProrationPaymentRequestedEvent {
    private final Long userId;
    private final Integer amount;
    
    public ProrationPaymentRequestedEvent(Long userId, Integer amount) {
        this.userId = userId;
        this.amount = amount;
    }
}
