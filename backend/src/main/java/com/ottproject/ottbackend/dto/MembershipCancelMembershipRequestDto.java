package com.ottproject.ottbackend.dto;

/**
 * 구독 해지 요청 DTO
 * - 즉시 해지 옵션 포함
 */
public class MembershipCancelMembershipRequestDto {
    public boolean immediate; // 즉시 해지 여부(true 면 즉시 종료)
}
