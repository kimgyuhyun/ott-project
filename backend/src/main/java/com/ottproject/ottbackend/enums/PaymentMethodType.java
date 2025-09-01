package com.ottproject.ottbackend.enums;

/**
 * 결제수단 타입 enum
 * 
 * 큰 흐름
 * - 결제수단의 종류를 구분하여 적절한 처리 로직을 적용한다.
 * 
 * 타입 개요
 * - CARD: 신용/체크카드
 * - ACCOUNT: 간편결제(카카오페이, 토스페이, 네이버페이 등)
 * - BANK_TRANSFER: 계좌이체
 */
public enum PaymentMethodType {
    CARD,           // 신용/체크카드
    KAKAO_PAY,      // 카카오페이
    TOSS_PAY,       // 토스페이먼츠
    NICE_PAY,       // 나이스페이먼츠
    BANK_TRANSFER   // 계좌이체
}