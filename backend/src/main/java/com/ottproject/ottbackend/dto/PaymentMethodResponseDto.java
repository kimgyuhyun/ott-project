package com.ottproject.ottbackend.dto; // DTO 패키지 선언

import java.time.LocalDateTime;
import com.ottproject.ottbackend.enums.PaymentMethodType;
import com.ottproject.ottbackend.enums.PaymentProvider;

/**
 * 결제수단 응답 DTO
 *
 * 큰 흐름
 * - 결제수단 목록/상세 조회 시 민감정보를 제외한 표시 정보만 제공한다.
 *
 * 필드 개요
 * - id/provider/type: 식별/게이트웨이/유형
 * - brand/last4/expiryMonth/expiryYear: 카드 마스킹/만료
 * - isDefault/priority/label: 기본 여부/우선순위/별칭
 * - createdAt/updatedAt: 생성/수정 시각
 */
public class PaymentMethodResponseDto { // 결제수단 응답 DTO 클래스 시작
	public Long id; // 결제수단 ID
	public PaymentProvider provider; // 결제 제공자(IMPORT)
	public PaymentMethodType type; // 결제수단 타입
	public String brand; // 카드 브랜드
	public String last4; // 카드 끝 4자리
	public Integer expiryMonth; // 만료 월
	public Integer expiryYear; // 만료 연도
	public boolean isDefault; // 기본 결제수단 여부
	public int priority; // 폴백 우선순위
	public String label; // 별칭
	public LocalDateTime createdAt; // 생성 시각
	public LocalDateTime updatedAt; // 수정 시각
}
