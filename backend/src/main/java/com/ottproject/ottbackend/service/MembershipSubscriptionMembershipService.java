package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * MembershipSubscriptionMembershipService
 *
 * 큰 흐름
 * - 구독 테이블 기준으로 사용자 멤버십 여부와 허용 최대 화질을 판별한다.
 *
 * 메서드 개요
 * - isMember: ACTIVE 상태의 유효 구독 존재 여부로 멤버십 여부 판단
 * - allowedMaxQuality: 멤버십 여부에 따라 최대 화질(1080p/720p) 반환
 */
@Service
@Lazy
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipSubscriptionMembershipService implements MembershipService { // 실제 멤버십 연동 기본 구현
	private final MembershipSubscriptionRepository membershipSubscriptionRepository; // 구독 조회 리포지토리

	@Override
	public boolean isMember(Long userId) {
		if (userId == null) return false; // 미로그인 비회원
		var now = LocalDateTime.now(); // 현재 시각
		return membershipSubscriptionRepository
				.findActiveEffectiveByUser(userId, MembershipSubscriptionStatus.ACTIVE, now)
				.isPresent(); // 유효 구독 존재 여부
	}

	@Override
	public String allowedMaxQuality(Long userId) {
		return isMember(userId) ? "1080p" : "720p"; // 멤버십 화질 상한
	}
}


