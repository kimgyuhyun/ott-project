package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.DailyStats;
import com.ottproject.ottbackend.enums.AuthEventType;
import com.ottproject.ottbackend.repository.AuthEventRepository;
import com.ottproject.ottbackend.repository.DailyStatsRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * StatsSnapshotService
 *
 * 큰 흐름
 * - 인증 이벤트(auth_events)와 가입 정보를 하루 단위로 집계해 DailyStats 스냅샷을 만든다.
 * - 매일 새벽 스케줄러가 전일자 스냅샷을 생성/갱신한다.
 * - 동일 일자를 다시 집계하면 기존 행을 덮어쓰므로(upsert) 재집계/백필이 안전하다.
 *
 * 메서드 개요
 * - snapshotYesterday: 매일 00:10(KST) 전일자 스냅샷 생성(스케줄)
 * - buildSnapshot: 특정 일자 스냅샷 생성/갱신(수동 재집계/백필용)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatsSnapshotService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AuthEventRepository authEventRepository; // 인증 이벤트 집계
    private final UserRepository userRepository; // 가입자 수 집계
    private final DailyStatsRepository dailyStatsRepository; // 스냅샷 적재

    /**
     * 매일 새벽 00:10(KST)에 전일자 스냅샷을 생성/갱신한다.
     * - 자정 직후 집계가 몰리지 않도록 10분 여유를 둔다.
     */
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void snapshotYesterday() {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        DailyStats stats = buildSnapshot(yesterday);
        log.info("일일 통계 스냅샷 생성 완료 - date={}, loginSuccess={}, loginFail={}, logout={}, signup={}, dau={}",
                stats.getStatDate(), stats.getLoginSuccessCount(), stats.getLoginFailCount(),
                stats.getLogoutCount(), stats.getSignupCount(), stats.getActiveUserCount());
    }

    /**
     * 특정 일자의 스냅샷을 생성/갱신한다(멱등).
     *
     * @param date 통계 기준 일자
     * @return 저장된 DailyStats
     */
    @Transactional
    public DailyStats buildSnapshot(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();          // 해당 일자 00:00:00 (포함)
        LocalDateTime end = date.plusDays(1).atStartOfDay(); // 다음 일자 00:00:00 (미포함)

        long loginSuccess = authEventRepository.countByTypeBetween(AuthEventType.LOGIN_SUCCESS, start, end);
        long loginFail = authEventRepository.countByTypeBetween(AuthEventType.LOGIN_FAIL, start, end);
        long logout = authEventRepository.countByTypeBetween(AuthEventType.LOGOUT, start, end);
        long signup = userRepository.countSignupsBetween(start, end);
        long dau = authEventRepository.countDistinctUsersByTypeBetween(AuthEventType.LOGIN_SUCCESS, start, end);

        // 기존 행이 있으면 덮어쓰고(upsert), 없으면 새로 생성
        DailyStats stats = dailyStatsRepository.findByStatDate(date)
                .orElseGet(() -> DailyStats.of(date));
        stats.updateCounts(loginSuccess, loginFail, logout, signup, dau);

        return dailyStatsRepository.save(stats);
    }
}
