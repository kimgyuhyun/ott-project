package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.admin.AuthEventDto;
import com.ottproject.ottbackend.dto.admin.DailyStatsDto;
import com.ottproject.ottbackend.repository.AuthEventRepository;
import com.ottproject.ottbackend.repository.DailyStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * AdminStatsService
 *
 * 큰 흐름
 * - 관리자 통계/감사 로그 조회를 담당한다. 조회 결과는 DTO 로만 내보낸다.
 * - 재집계는 StatsSnapshotService 에 위임하고 응답 변환만 맡는다.
 *
 * 왜 이 클래스가 생겼는가
 * - 이전에는 AdminStatsController 가 리포지토리를 직접 호출하고 엔티티를 그대로 응답했다.
 *   조회 기간 계산(KST 기준 N일 구간)도 컨트롤러에 있었다.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyStatsRepository dailyStatsRepository;
    private final AuthEventRepository authEventRepository;
    private final StatsSnapshotService statsSnapshotService;

    /**
     * 최근 N일 일일 통계 조회(오늘 포함, 일자 오름차순).
     *
     * @param days 조회할 일수(오늘 포함)
     * @return 기간 내 일일 통계 목록
     */
    @Transactional(readOnly = true)
    public List<DailyStatsDto> listDaily(int days) {
        LocalDate to = LocalDate.now(KST);
        LocalDate from = to.minusDays(Math.max(0, days - 1)); // days=30 이면 오늘 포함 30일 구간
        return dailyStatsRepository.findByStatDateBetweenOrderByStatDateAsc(from, to).stream()
                .map(DailyStatsDto::from)
                .toList();
    }

    /**
     * 특정 일자 스냅샷 재집계(백필/검증용). 같은 일자에 여러 번 실행해도 결과가 같다.
     *
     * @param date 재집계할 일자
     * @return 재집계된 통계
     */
    public DailyStatsDto rebuild(LocalDate date) {
        return DailyStatsDto.from(statsSnapshotService.buildSnapshot(date));
    }

    /**
     * 최근 인증 이벤트 100건 조회(최신순).
     *
     * @return 인증 이벤트 목록
     */
    @Transactional(readOnly = true)
    public List<AuthEventDto> recentAuthEvents() {
        return authEventRepository.findTop100ByOrderByOccurredAtDesc().stream()
                .map(AuthEventDto::from)
                .toList();
    }
}
