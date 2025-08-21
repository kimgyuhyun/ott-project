package com.ottproject.ottbackend.enums; // 결제수단 타입 정의가 위치할 패키지 선언

/**
 * 결제수단 타입
 *
 * 큰 흐름
 * - 저장 가능한 결제수단의 유형을 구분한다.
 *
 * 상수 개요
 * - CARD/ACCOUNT
 */
public enum PaymentMethodType { // 결제수단 유형
    CARD, // 카드 기반
    ACCOUNT // 계좌 기반
}