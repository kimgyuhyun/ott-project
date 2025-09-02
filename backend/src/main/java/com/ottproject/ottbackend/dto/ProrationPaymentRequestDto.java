package com.ottproject.ottbackend.dto;

import lombok.Data;

/**
 * ProrationPaymentRequestDto
 *
 * 큰 흐름
 * - 차액 결제 요청 시 필요한 정보를 담는 DTO
 *
 * 필드 개요
 * - planCode: 대상 플랜 코드
 * - successUrl: 성공 시 리다이렉트 URL
 * - cancelUrl: 취소 시 리다이렉트 URL
 * - paymentService: 결제 서비스 (kakao, toss, nice 등)
 */
@Data
public class ProrationPaymentRequestDto {
    private String planCode; // 대상 플랜 코드
    private String successUrl; // 성공 URL
    private String cancelUrl; // 취소 URL
    private String paymentService; // 결제 서비스
}
