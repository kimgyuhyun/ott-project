package com.ottproject.ottbackend.dto;

import lombok.Data;

/**
 * 멤버십 플랜 변경 요청 DTO
 *
 * 큰 흐름
 * - 사용자가 멤버십 플랜을 변경할 때 사용하는 요청 데이터
 * - 새로운 플랜 코드를 받아서 업그레이드/다운그레이드를 판단한다
 *
 * 필드 개요
 * - newPlanCode: 변경할 플랜의 코드 (BASIC, PREMIUM, ULTIMATE)
 */
@Data
public class MembershipPlanChangeRequestDto {
    
    private String newPlanCode; // 변경할 플랜 코드
}
