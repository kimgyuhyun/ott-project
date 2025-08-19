package com.ottproject.ottbackend.mybatis;

import com.ottproject.ottbackend.dto.MembershipPlanDto;
import com.ottproject.ottbackend.dto.UserMembershipDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 멤버십 조회용 MyBatis 매퍼
 * - 플랜 목록 간단 조회
 * - 내 멤버십 상태 조회
 */
@Mapper
public interface MembershipQueryMapper { // MyBatis 매퍼
    List<MembershipPlanDto> listPlans(); // 플랜 목록

    UserMembershipDto findMyMembership(@Param("userId") Long userId, @Param("now") java.time.LocalDateTime now); // 내 멤버십 단건
}
