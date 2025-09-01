package com.ottproject.ottbackend.enums; // 결제 제공자 enum 패키지 선언

/**
 * 결제 제공자(게이트웨이) 종류
 *
 * 큰 흐름
 * - 외부 결제 게이트웨이 공급자를 구분한다.
 *
 * 상수 개요
 * - IMPORT
 */
public enum PaymentProvider { // 게이트웨이 식별자
    STRIPE,     // Stripe
    IMPORT,     // 아임포트
    TOSS,       // 토스페이먼츠
    KAKAO       // 카카오페이
}