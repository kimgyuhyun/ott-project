package com.ottproject.ottbackend.mybatis;

import com.ottproject.ottbackend.entity.MembershipSubscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MembershipSubscriptionQueryMapper
 *
 * 큰 흐름
 * - 정기결제 배치에서 사용할 구독 조회를 담당하는 MyBatis 매퍼
 *
 * 메서드 개요
 * - findSubscriptionsForBilling: 정기결제 대상 구독 조회
 */
@Mapper
public interface MembershipSubscriptionQueryMapper {
    
    /**
     * 정기결제 대상 구독 조회
     * - ACTIVE/PAST_DUE 상태이면서 자동갱신 ON이고 말일 해지 예약이 아닌 구독
     * - nextBillingAt이 현재 시각 이하인 구독만 조회
     */
    List<MembershipSubscription> findSubscriptionsForBilling(
        @Param("statuses") List<String> statuses,
        @Param("now") LocalDateTime now
    );

    /**
     * 플랜 변경 예약된 구독 조회
     * - nextBillingAt이 현재 시각 이하이고 nextPlanId가 있는 구독
     * - 정기 결제 시 플랜 변경을 적용할 대상 구독들
     */
    List<MembershipSubscription> findSubscriptionsWithScheduledPlanChanges(
        @Param("now") LocalDateTime now
    );
}
