package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.AuthEvent;
import com.ottproject.ottbackend.enums.AuthEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AuthEventRepository
 *
 * 큰 흐름
 * - 인증 이벤트(감사 로그)의 적재/조회 및 통계 집계용 카운트 쿼리를 제공한다.
 * - 경계값 중복 집계를 피하기 위해 기간 조건은 [start, end) (start 이상, end 미만) 으로 통일한다.
 *
 * 메서드 개요
 * - countByTypeBetween: 특정 유형의 기간 내 발생 건수
 * - countDistinctUsersByTypeBetween: 특정 유형의 기간 내 고유 사용자 수(DAU 산출용)
 * - findTop100ByOrderByOccurredAtDesc: 최근 이벤트 목록(관리자 모니터링용)
 */
@Repository
public interface AuthEventRepository extends JpaRepository<AuthEvent, Long> {

    /**
     * 특정 유형의 기간 내 발생 건수
     */
    @Query("SELECT COUNT(e) FROM AuthEvent e " +
            "WHERE e.eventType = :type AND e.occurredAt >= :start AND e.occurredAt < :end")
    long countByTypeBetween(@Param("type") AuthEventType type,
                            @Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);

    /**
     * 특정 유형의 기간 내 고유 사용자 수 (DAU: 일일 활성 사용자 산출용)
     * - userId 가 null 인(미식별) 이벤트는 제외한다.
     */
    @Query("SELECT COUNT(DISTINCT e.userId) FROM AuthEvent e " +
            "WHERE e.eventType = :type AND e.userId IS NOT NULL " +
            "AND e.occurredAt >= :start AND e.occurredAt < :end")
    long countDistinctUsersByTypeBetween(@Param("type") AuthEventType type,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    /**
     * 최근 인증 이벤트 100건 (관리자 모니터링용)
     */
    List<AuthEvent> findTop100ByOrderByOccurredAtDesc();
}
