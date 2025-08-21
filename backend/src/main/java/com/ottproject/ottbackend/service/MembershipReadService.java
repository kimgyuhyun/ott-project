package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.MembershipPlanDto;
import com.ottproject.ottbackend.dto.UserMembershipDto;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.mybatis.MembershipQueryMapper;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MembershipReadService
 *
 * 큰 흐름
 * - 멤버십 플랜/내 구독 상태를 조회하는 읽기 전용 서비스(MyBatis).
 *
 * 메서드 개요
 * - listPlans: 플랜 목록
 * - getMyMembership: 사용자 현재 구독 상태(없으면 EXPIRED 기본값)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipReadService { // 멤버십 읽기
    private final MembershipQueryMapper membershipQueryMapper; // MyBatis 매퍼(플랜 목록)

    /**
     * 플랜 목록 조회
     */
    public List<MembershipPlanDto> listPlans() { // 플랜 목록
        return membershipQueryMapper.listPlans(); // 매퍼 호출
    }

    /**
     * 내 멤버십 상태 조회(MyBatis)
     * - 유효 구독이 없으면 EXPIRED 기본값 반환
     */
    public UserMembershipDto getMyMembership(Long userId) { // 내 멤버십 상태
        LocalDateTime now = LocalDateTime.now(); // 기준 시각
        UserMembershipDto dto = membershipQueryMapper.findMyMembership(userId, now); // MyBatis 단건 조회
        if (dto == null) { // 유효 구독 없음
            dto = new UserMembershipDto(); // 기본 DTO 생성
            dto.status = MembershipSubscriptionStatus.EXPIRED; // 만료 표기
            dto.autoRenew = false; // 자동갱신 없음
        }
        return dto; // 반환
    }
}
