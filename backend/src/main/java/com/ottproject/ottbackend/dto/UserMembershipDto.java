package com.ottproject.ottbackend.dto;

import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;

import java.time.LocalDateTime;

/**
 * 내 멤버십 상태 DTO
 *
 * 큰 흐름
 * - 현재 구독 플랜/만료일/자동갱신/상태를 뷰에 전달한다.
 * - 플랜 변경 예약 시 다음 결제일에 적용될 플랜 정보도 포함한다.
 *
 * 필드 개요
 * - planCode/planName: 현재 플랜 코드/명칭
 * - endAt/nextBillingAt/autoRenew/status: 만료일/다음결제일/자동갱신/상태
 * - nextPlanCode/nextPlanName: 다음 결제일부터 적용될 플랜 (플랜 변경 예약 시)
 */
public class UserMembershipDto {
    public String planCode; // 현재 플랜 코드
    public String planName; // 현재 플랜 이름
    public LocalDateTime endAt; // 만료일
    public LocalDateTime nextBillingAt; // 다음 결제일
    public boolean autoRenew; // 자동 갱신
    public MembershipSubscriptionStatus status; // 상태
    
    // 플랜 변경 예약 시 사용
    public String nextPlanCode; // 다음 결제일부터 적용될 플랜 코드
    public String nextPlanName; // 다음 결제일부터 적용될 플랜 이름
}
