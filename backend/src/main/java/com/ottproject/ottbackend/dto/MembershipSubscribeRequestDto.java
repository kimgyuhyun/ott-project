package com.ottproject.ottbackend.dto;

/**
 * 구독 신청 요청 DTO
 * - 결제 연동 전 단계: 플랜 코드로 신청
 */
public class MembershipSubscribeRequestDto {
    public String planCode; // 신청 플랜 코드
}
