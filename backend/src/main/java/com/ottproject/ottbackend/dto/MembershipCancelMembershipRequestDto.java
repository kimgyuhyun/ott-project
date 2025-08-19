package com.ottproject.ottbackend.dto;

/**
 * 구독 해지 요청 DTO
 * - 말일 해지 고정
 * - 아이드엠포턴시 토큰으로 중복요청 방지
 */
public class MembershipCancelMembershipRequestDto {
    public String idempotencyKey; // 중복 방지용 토큰(선택)
}


