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
	
	/**
	 * 사용자 멤버십 활성 상태 확인
	 * - 실시간으로 멤버십 상태를 확인하여 정확한 정보 반환
	 */
	public boolean isUserMembershipActive(Long userId) {
		if (userId == null) return false; // 미로그인 비회원
		var now = LocalDateTime.now(); // 현재 시각
		return membershipSubscriptionRepository
				.findActiveEffectiveByUser(userId, MembershipSubscriptionStatus.ACTIVE, now)
				.isPresent(); // 유효 구독 존재 여부
	}
	
	/**
	 * 사용자 멤버십 상세 정보 조회
	 * - 멤버십 상태, 시작/종료 날짜, 자동갱신 여부 등 상세 정보 반환
	 */
	public MembershipSubscriptionDetails getUserMembershipDetails(Long userId) {
		if (userId == null) return null; // 미로그인 비회원
		
		var now = LocalDateTime.now(); // 현재 시각
		var subscription = membershipSubscriptionRepository
				.findActiveEffectiveByUser(userId, MembershipSubscriptionStatus.ACTIVE, now);
		
		if (subscription.isEmpty()) {
			return null; // 활성 구독 없음
		}
		
		var sub = subscription.get();
		return MembershipSubscriptionDetails.builder()
				.isActive(true)
				.startDate(sub.getStartAt())
				.endDate(sub.getEndAt())
				.autoRenew(sub.isAutoRenew())
				.planCode(sub.getMembershipPlan().getCode())
				.planName(sub.getMembershipPlan().getName())
				.build();
	}
	
	/**
	 * 멤버십 구독 상세 정보 DTO
	 */
	public static class MembershipSubscriptionDetails {
		public final boolean isActive;
		public final LocalDateTime startDate;
		public final LocalDateTime endDate;
		public final boolean autoRenew;
		public final String planCode;
		public final String planName;
		
		private MembershipSubscriptionDetails(Builder builder) {
			this.isActive = builder.isActive;
			this.startDate = builder.startDate;
			this.endDate = builder.endDate;
			this.autoRenew = builder.autoRenew;
			this.planCode = builder.planCode;
			this.planName = builder.planName;
		}
		
		public static Builder builder() {
			return new Builder();
		}
		
		public static class Builder {
			private boolean isActive;
			private LocalDateTime startDate;
			private LocalDateTime endDate;
			private boolean autoRenew;
			private String planCode;
			private String planName;
			
			public Builder isActive(boolean isActive) {
				this.isActive = isActive;
				return this;
			}
			
			public Builder startDate(LocalDateTime startDate) {
				this.startDate = startDate;
				return this;
			}
			
			public Builder endDate(LocalDateTime endDate) {
				this.endDate = endDate;
				return this;
			}
			
			public Builder autoRenew(boolean autoRenew) {
				this.autoRenew = autoRenew;
				return this;
			}
			
			public Builder planCode(String planCode) {
				this.planCode = planCode;
				return this;
			}
			
			public Builder planName(String planName) {
				this.planName = planName;
				return this;
			}
			
			public MembershipSubscriptionDetails build() {
				return new MembershipSubscriptionDetails(this);
			}
		}
	}
}


