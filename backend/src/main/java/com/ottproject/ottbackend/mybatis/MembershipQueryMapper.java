package com.ottproject.ottbackend.mybatis;

import com.ottproject.ottbackend.dto.MembershipPlanDto;
import com.ottproject.ottbackend.dto.UserMembershipDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MembershipQueryMapper
 *
 * 큰 흐름
 * - 멤버십 플랜/내 구독 상태 조회를 담당하는 MyBatis 매퍼.
 *
 * 메서드 개요
 * - listPlans: 플랜 목록
 * - findMyMembership: 사용자 현재 구독 상태
 */
@Mapper
public interface MembershipQueryMapper { // MyBatis 매퍼
    List<MembershipPlanDto> listPlans(); // 플랜 목록

    UserMembershipDto findMyMembership(@Param("userId") Long userId, @Param("now") java.time.LocalDateTime now); // 내 멤버십 단건
}
