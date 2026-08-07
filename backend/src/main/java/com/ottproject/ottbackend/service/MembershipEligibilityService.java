package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * MembershipEligibilityService
 *
 * 큰 흐름
 * - 구독 테이블 기준으로 사용자 멤버십 자격을 판별한다.
 *
 * 메서드 개요
 * - isMember: ACTIVE 상태의 유효 구독 존재 여부로 멤버십 여부 판단
 */
@Service
@Lazy
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipEligibilityService { // 멤버십 자격 판별
	private final MembershipSubscriptionRepository membershipSubscriptionRepository; // 구독 조회 리포지토리

	public boolean isMember(Long userId) {
		if (userId == null) return false; // 미로그인 비회원
		var now = LocalDateTime.now(); // 현재 시각
		return membershipSubscriptionRepository
				.findActiveEffectiveByUser(userId, MembershipSubscriptionStatus.ACTIVE, now)
				.isPresent(); // 유효 구독 존재 여부
	}
}
