package com.ottproject.ottbackend.service;

/**
 * 멤버십 판별 추상화
 * - 구현체에 따라 멤버십 여부/허용 화질 상한을 결정
 */
public interface MembershipService { // 멤버십 판별 추상화
	boolean isMember(Long userId);
	String allowedMaxQuality(Long userId); // "1080p" | "720p"
}


