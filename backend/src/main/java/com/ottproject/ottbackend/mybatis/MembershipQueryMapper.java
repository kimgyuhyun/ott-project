package com.ottproject.ottbackend.mybatis;

import com.ottproject.ottbackend.dto.MembershipPlanDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 멤버십 조회용 MyBatis 매퍼
 * - 플랜 목록 간단 조회
 */
@Mapper
public interface MembershipQueryMapper { // MyBatis 매퍼
    List<MembershipPlanDto> listPlans(); // 플랜 목록
}
