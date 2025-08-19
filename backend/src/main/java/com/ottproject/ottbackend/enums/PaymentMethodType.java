package com.ottproject.ottbackend.enums; // 결제수단 타입 정의가 위치할 패키지 선언

/**
 * 결제수단 타입
 *
 * - CARD: 신용/체크카드 기반 결제
 * - ACCOUNT: 계좌이체/간편계좌 기반 결제
 *
 * 비고:
 * - 필요 시 VIRTUAL_ACCOUNT(가상계좌), WALLET 등의 타입 추가 가능
 */
public enum PaymentMethodType { // 등록 가능한 결제수단의 유형을 나타내는 enum 선언
    CARD, // 카드 기반 결제수단
    ACCOUNT // 계좌 기반 결제수단
}