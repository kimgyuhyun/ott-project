package com.ottproject.ottbackend.dto;

import com.ottproject.ottbackend.enums.PlanChangeType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 멤버십 플랜 변경 응답 DTO
 *
 * 큰 흐름
 * - 플랜 변경 요청 처리 결과를 클라이언트에 반환
 * - 변경 유형, 적용일, 차액, 안내 메시지를 포함한다
 *
 * 필드 개요
 * - changeType: 변경 유형 (UPGRADE/DOWNGRADE)
 * - effectiveDate: 변경 적용일
 * - prorationAmount: 차액 (업그레이드 시에만)
 * - message: 사용자 안내 메시지
 */
@Data
@Builder
public class MembershipPlanChangeResponseDto {
    
    private PlanChangeType changeType; // 변경 유형
    private LocalDateTime effectiveDate; // 변경 적용일
    private Integer prorationAmount; // 차액 (업그레이드 시에만)
    private String message; // 사용자 안내 메시지
}
