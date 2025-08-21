package com.ottproject.ottbackend.service;

/**
 * 멤버십 판별 추상화
 *
 * 큰 흐름(Javadoc):
 * - 콘텐츠 접근 권한/화질 제한 판단을 위해 사용자 멤버십 여부를 조회하는 인터페이스입니다.
 * - 기본 구현은 구독 테이블을 조회하는 {@link MembershipSubscriptionMembershipService}가 제공합니다.
 *
 * 메서드 개요:
 * - isMember: 사용자 멤버십 여부를 판별합니다.
 * - allowedMaxQuality: 멤버십 여부에 따른 허용 최대 화질(예: 1080p/720p)을 반환합니다.
 */
public interface MembershipService { // 멤버십 추상화 인터페이스
    boolean isMember(Long userId); // 사용자 멤버십 여부
    String allowedMaxQuality(Long userId); // 허용 최대 화질
}


