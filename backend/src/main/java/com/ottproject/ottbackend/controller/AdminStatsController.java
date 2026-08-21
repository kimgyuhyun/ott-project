package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.admin.AuthEventDto;
import com.ottproject.ottbackend.dto.admin.DailyStatsDto;
import com.ottproject.ottbackend.service.AdminStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AdminStatsController
 *
 * 큰 흐름
 * - 관리자 전용 통계/감사 로그 조회 API 를 제공한다.
 * - 일일 통계는 미리 집계된 스냅샷을 조회하므로 빠르다.
 * - 보안: SecurityConfig 에서 /api/admin/stats/** 는 ROLE_ADMIN 으로 제한한다.
 *
 * 엔드포인트 개요
 * - GET  /api/admin/stats/daily: 최근 N일 일일 통계 목록
 * - POST /api/admin/stats/daily/rebuild: 특정 일자 스냅샷 수동 재집계(백필)
 * - GET  /api/admin/stats/auth-events: 최근 인증 이벤트 100건
 */
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    /**
     * 최근 N일 일일 통계 목록 조회(기본 30일)
     *
     * @param days 조회할 일수(오늘 포함)
     * @return 기간 일일 통계 목록(오름차순)
     */
    @Operation(summary = "일일 통계 목록", description = "최근 N일간의 일일 통계 스냅샷을 조회한다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @GetMapping("/daily")
    public ResponseEntity<List<DailyStatsDto>> daily(
            @Parameter(description = "조회 일수(오늘 포함, 기본 30)") @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(adminStatsService.listDaily(days));
    }

    /**
     * 특정 일자 스냅샷 수동 재집계(백필/검증용)
     *
     * @param date 재집계할 일자(yyyy-MM-dd)
     * @return 재집계된 일일 통계
     */
    @Operation(summary = "일일 통계 재집계", description = "특정 일자의 스냅샷을 즉시 재집계한다(멱등).")
    @ApiResponse(responseCode = "200", description = "성공")
    @PostMapping("/daily/rebuild")
    public ResponseEntity<DailyStatsDto> rebuild(
            @Parameter(description = "재집계 일자(yyyy-MM-dd)") @RequestParam String date) {
        return ResponseEntity.ok(adminStatsService.rebuild(LocalDate.parse(date)));
    }

    /**
     * 최근 인증 이벤트 100건 조회(모니터링/추적용)
     *
     * @return 최근 인증 이벤트 목록(최신순)
     */
    @Operation(summary = "최근 인증 이벤트", description = "최근 인증 이벤트 100건을 최신순으로 조회한다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @GetMapping("/auth-events")
    public ResponseEntity<List<AuthEventDto>> recentAuthEvents() {
        return ResponseEntity.ok(adminStatsService.recentAuthEvents());
    }
}
