package com.ottproject.ottbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 금액/통화 VO(@Embeddable)
 *
 * 큰 흐름
 * - 최소 화폐단위 기준 금액(amount)과 통화(currency)를 하나의 타입으로 캡슐화한다.
 * - 생성 시 유효성(음수 금지, 통화 제한)을 검증하여 도메인 불변식을 보장한다.
 * - 엔티티에 임베드되어 금액/통화가 항상 함께 이동하도록 강제한다.
 *
 * 필드 개요
 * - amount: 최소 화폐단위 금액(Long)
 * - currency: 통화 코드(ISO 4217, KRW/USD)
 *
 * 메서드 개요
 * - Money(Long, String): 금액/통화 유효성 검증 후 생성
 * - isSupportedCurrency(String): 지원 통화 여부 검사
 * - getAmount()/getCurrency(): 불변 게터
 */
@Embeddable // JPA 임베디드 값 객체로 매핑
public class Money { // 금액 VO 시작

    @Column(name = "amount", nullable = false) // 컬럼명 유지: payments.amount
    private Long amount; // 최소 화폐단위 금액

    @Column(name = "currency", length = 3, nullable = false) // 컬럼명 유지: payments.currency
    private String currency; // 통화 코드(ISO 4217) - KRW/USD 지원

    protected Money() { // JPA 기본 생성자
    }

    public Money(Long amount, String currency) { // 주 생성자: 유효성 검증
        if (amount == null || amount < 0) throw new IllegalArgumentException("amount must be >= 0"); // 음수 금지
        if (!isSupportedCurrency(currency)) throw new IllegalArgumentException("currency must be KRW or USD"); // 통화 제한
        this.amount = amount; // 필드 설정
        this.currency = currency; // 필드 설정
    }

    private boolean isSupportedCurrency(String currency) { // 지원 통화 검증
        return "KRW".equals(currency) || "USD".equals(currency); // KRW 또는 USD만 허용
    }

    public Long getAmount() { // 금액 게터
        return amount; // 값 반환
    }

    public String getCurrency() { // 통화 게터
        return currency; // 값 반환
    }
}


