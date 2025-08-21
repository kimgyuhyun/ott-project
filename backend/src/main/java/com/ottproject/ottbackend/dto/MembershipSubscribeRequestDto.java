package com.ottproject.ottbackend.dto;

/**
 * 구독 신청 요청 DTO
 *
 * 큰 흐름
 * - 결제 연동 전, 선택한 플랜 코드로 구독을 신청한다.
 * - 서버에서 결제 플로우(체크아웃)로 이어진다.
 *
 * 필드 개요
 * - planCode: 신청 플랜 코드
 */
public class MembershipSubscribeRequestDto {
    public String planCode; // 신청 플랜 코드
}
