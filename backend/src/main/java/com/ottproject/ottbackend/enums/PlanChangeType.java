package com.ottproject.ottbackend.enums;

/**
 * 플랜 변경 타입 enum
 *
 * 큰 흐름
 * - 멤버십 플랜 변경 시 업그레이드/다운그레이드를 구분한다.
 * - 각 타입별로 다른 비즈니스 로직이 적용된다.
 *
 * 타입 개요
 * - UPGRADE: 상위 플랜으로 변경 (즉시 적용 + 차액 결제)
 * - DOWNGRADE: 하위 플랜으로 변경 (다음 결제일부터 적용)
 */
public enum PlanChangeType {
    UPGRADE,    // 업그레이드: 즉시 적용 + 차액 결제
    DOWNGRADE   // 다운그레이드: 다음 결제일부터 적용
}
