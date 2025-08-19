package com.ottproject.ottbackend.dto; // DTO 패키지 선언

import com.ottproject.ottbackend.enums.PaymentMethodType;
import com.ottproject.ottbackend.enums.PaymentProvider;

/**
 * 결제수단 등록 요청 DTO
 *
 * 역할:
 * - 사용자가 카드/계좌 등 결제수단을 등록할 때 사용
 * - 현재 게이트웨이는 IMPORT 기준이나, 확장성을 위해 provider 필드 유지
 */
public class PaymentMethodRegisterRequestDto { // 결제수단 등록 요청 DTO 클래스 시작
	public PaymentProvider provider; // 결제 제공자(IMPORT 고정 권장)
	public PaymentMethodType type; // 결제수단 타입(CARD/ACCOUNT)
	public String providerMethodId; // 외부 결제수단 식별자(토큰/키)
	public String brand; // 카드 브랜드(선택)
	public String last4; // 카드 끝 4자리(선택)
	public Integer expiryMonth; // 만료 월(선택)
	public Integer expiryYear; // 만료 연도(선택)
	public boolean isDefault; // 기본 결제수단 여부
	public int priority; // 폴백 우선순위(낮을수록 우선)
	public String label; // 별칭(선택)
}
