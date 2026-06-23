package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.DailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * DailyStatsRepository
 *
 * 큰 흐름
 * - 일일 통계 스냅샷의 적재/조회를 제공한다.
 *
 * 메서드 개요
 * - findByStatDate: 특정 일자 스냅샷 조회(재집계 시 기존 행 upsert 용)
 * - findByStatDateBetweenOrderByStatDateAsc: 기간 스냅샷 목록(차트용)
 */
@Repository
public interface DailyStatsRepository extends JpaRepository<DailyStats, Long> {

    Optional<DailyStats> findByStatDate(LocalDate statDate); // 특정 일자 스냅샷

    List<DailyStats> findByStatDateBetweenOrderByStatDateAsc(LocalDate from, LocalDate to); // 기간 스냅샷(오름차순)
}
