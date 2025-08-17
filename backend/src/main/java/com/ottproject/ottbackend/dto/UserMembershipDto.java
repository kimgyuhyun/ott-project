package com.ottproject.ottbackend.dto;

import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;

import java.time.LocalDateTime;

/**
 * 내 멤버십 상태 DTO
 * - 현재 플랜/만료일/자동갱신/상태
 */
public class UserMembershipDto {
    public String planCode; // 플랜 코드
    public String planName; // 플랜 이름
    public LocalDateTime endAt; // 만료일
    public boolean autoRenew; // 자동 갱신
    public MembershipSubscriptionStatus status; // 상태
}
