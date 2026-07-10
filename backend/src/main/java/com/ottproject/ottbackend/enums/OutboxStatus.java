package com.ottproject.ottbackend.enums;

/**
 * 아웃박스 이벤트 발행 상태
 *
 * 큰 흐름
 * - NEW: 아직 카프카로 발행되지 않은 이벤트(폴링 대상)
 * - PUBLISHED: 카프카로 발행 완료된 이벤트
 */
public enum OutboxStatus {
    NEW, // 발행 대기
    PUBLISHED // 발행 완료
}
