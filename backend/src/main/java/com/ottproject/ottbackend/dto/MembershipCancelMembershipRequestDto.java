package com.ottproject.ottbackend.dto;

/**
 * 구독 해지 요청 DTO
 *
 * 큰 흐름
 * - 말일 해지 예약을 요청한다.
 * - idempotencyKey 로 중복 요청을 방지한다.
 *
 * 필드 개요
 * - idempotencyKey: 멱등 토큰(선택)
 */
public class MembershipCancelMembershipRequestDto {
    public String idempotencyKey; // 중복 방지용 토큰(선택)
}


