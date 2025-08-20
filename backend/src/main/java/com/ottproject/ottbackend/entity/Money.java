package com.ottproject.ottbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 금액/통화 VO(@Embeddable)
 *
 * 큰 흐름(Javadoc):
 * - 최소 화폐단위 기준의 금액(amount)과 통화 코드(currency)를 한 덩어리로 다룹니다.
 * - 생성 시 유효성(KRW/USD만 허용, 음수 금지)을 검증해 도메인 불변식을 보장합니다.
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


