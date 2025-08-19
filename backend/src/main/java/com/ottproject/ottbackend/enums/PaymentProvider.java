package com.ottproject.ottbackend.enums; // 결제 제공자 enum 패키지 선언

/**
 * 결제 제공자(게이트웨이) 종류
 *
 * - IMPORT: 아임포트(국내 PG 허브) 연동
 *
 * 비고:
 * - application-dev.yml 기준으로 아임포트 설정(merchant/rest/webhook/pg.*)만 존재
 * - 개별 PG(카카오/토스/나이스)는 아임포트 내부 채널로 사용되므로 별도 Provider로 구분하지 않음
 */
public enum PaymentProvider { // 외부 결제 게이트웨이를 구분하는 enum 선언
    IMPORT // 아임포트 결제 제공자
}