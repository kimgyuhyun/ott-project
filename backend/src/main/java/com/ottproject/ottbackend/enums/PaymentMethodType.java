package com.ottproject.ottbackend.enums;

/**
 * 결제수단 타입 enum
 * 
 * 큰 흐름
 * - 아임포트 pay_method와 1:1 매핑되는 결제수단 타입을 정의한다.
 * - 각 결제 수단별로 명확한 구분을 제공한다.
 * 
 * 타입 개요
 * - CARD: 신용/체크카드 (pay_method: "card")
 * - KAKAO_PAY: 카카오페이 (pay_method: "kakaopay")
 * - TOSS_PAY: 토스페이 (pay_method: "tosspayments")
 * - NICE_PAY: 나이스페이 (pay_method: "nice")
 * - BANK_TRANSFER: 계좌이체
 */
public enum PaymentMethodType {
    CARD,           // 신용/체크카드 (pay_method: "card")
    KAKAO_PAY,      // 카카오페이 (pay_method: "kakaopay")
    TOSS_PAY,       // 토스페이 (pay_method: "tosspayments")
    NICE_PAY,       // 나이스페이 (pay_method: "nice")
    BANK_TRANSFER   // 계좌이체
}