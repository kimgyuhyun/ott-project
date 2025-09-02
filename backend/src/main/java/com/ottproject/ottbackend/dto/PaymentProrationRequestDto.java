package com.ottproject.ottbackend.dto;

import lombok.Data;

/**
 * 차액 결제 요청 DTO
 *
 * 큰 흐름
 * - 멤버십 플랜 업그레이드 시 차액 결제에 사용
 * - 개발 환경에서는 1원으로 처리된다
 *
 * 필드 개요
 * - amount: 차액 금액
 * - description: 결제 설명
 */
@Data
public class PaymentProrationRequestDto {
    
    private Integer amount; // 차액 금액
    private String description; // 결제 설명
}
