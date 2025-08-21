package com.ottproject.ottbackend.dto; // DTO 패키지 선언

import com.ottproject.ottbackend.enums.PaymentMethodType;
import com.ottproject.ottbackend.enums.PaymentProvider;

/**
 * 결제수단 등록 요청 DTO
 *
 * 큰 흐름
 * - 카드/계좌 등 결제수단을 저장 등록할 때 사용한다.
 * - 게이트웨이 확장성을 위해 provider/type/외부 식별자를 받는다.
 *
 * 필드 개요
 * - provider/type/providerMethodId: 게이트웨이/유형/외부 식별자
 * - brand/last4/expiryMonth/expiryYear: 카드 마스킹/만료
 * - isDefault/priority/label: 기본 여부/우선순위/별칭
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
