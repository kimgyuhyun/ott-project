package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.enums.SubscriptionStatus;
import com.ottproject.ottbackend.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 구독 기반 멤버십 판별 서비스 구현
 * - ACTIVE 상태이며 기간 유효한 구독 존재 시 멤버십 인정
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionMembershipService implements MembershipService { // 실제 멤버십 연동 기본 구현
	private final SubscriptionRepository subscriptionRepository; // 구독 조회 리포지토리

	@Override
	public boolean isMember(Long userId) {
		if (userId == null) return false; // 미로그인 비회원
		var now = LocalDateTime.now(); // 현재 시각
		return subscriptionRepository
				.findFirstByUser_IdAndStatusAndStartAtBeforeAndEndAtAfterOrEndAtIsNullOrderByStartAtDesc(
						userId, SubscriptionStatus.ACTIVE, now, now
				).isPresent(); // 유효 구독 존재 여부
	}

	@Override
	public String allowedMaxQuality(Long userId) {
		return isMember(userId) ? "1080p" : "720p"; // 멤버십 화질 상한
	}
}


