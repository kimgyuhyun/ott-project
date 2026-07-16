package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.DailyStats;
import com.ottproject.ottbackend.enums.AuthEventType;
import com.ottproject.ottbackend.repository.AuthEventRepository;
import com.ottproject.ottbackend.repository.DailyStatsRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * StatsSnapshotService 일일 통계 집계 검증
 *
 * 왜 이 테스트가 필요한가
 * - 집계 5종이 모두 long 이라 서로 자리가 바뀌어도 컴파일이 통과한다.
 *   로그인 성공/실패가 뒤바뀐 채 배포돼도 스냅샷만 보면 알아채기 어렵다.
 * - 하루 경계는 [당일 00:00, 익일 00:00) 반열린 구간이다. 끝을 포함하면 자정 이벤트가 이틀에 중복 집계된다.
 * - 재집계(백필)는 같은 일자 행을 덮어써야 한다. 새 행을 만들면 unique 제약에 걸린다.
 */
@ExtendWith(MockitoExtension.class)
class StatsSnapshotServiceTest {

    @Mock private AuthEventRepository authEventRepository;
    @Mock private UserRepository userRepository;
    @Mock private DailyStatsRepository dailyStatsRepository;

    @InjectMocks private StatsSnapshotService statsSnapshotService;

    private static final LocalDate DATE = LocalDate.of(2026, 7, 16);
    private static final LocalDateTime START = LocalDateTime.of(2026, 7, 16, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 7, 17, 0, 0);

    /**
     * 집계값을 서로 다른 수로 준비해, 자리가 바뀌면 반드시 드러나게 한다.
     */
    private void givenCounts(long loginSuccess, long loginFail, long logout, long signup, long dau) {
        given(authEventRepository.countByTypeBetween(AuthEventType.LOGIN_SUCCESS, START, END)).willReturn(loginSuccess);
        given(authEventRepository.countByTypeBetween(AuthEventType.LOGIN_FAIL, START, END)).willReturn(loginFail);
        given(authEventRepository.countByTypeBetween(AuthEventType.LOGOUT, START, END)).willReturn(logout);
        given(userRepository.countSignupsBetween(START, END)).willReturn(signup);
        given(authEventRepository.countDistinctUsersByTypeBetween(AuthEventType.LOGIN_SUCCESS, START, END))
                .willReturn(dau);
    }

    @Test
    @DisplayName("집계값이 각각 제 필드에 담긴다 - 로그인 성공/실패/로그아웃이 뒤섞이면 안 된다")
    void mapsEachCountToItsOwnField() {
        givenCounts(11L, 22L, 33L, 44L, 55L);
        given(dailyStatsRepository.findByStatDate(DATE)).willReturn(Optional.empty());
        given(dailyStatsRepository.save(any(DailyStats.class))).willAnswer(inv -> inv.getArgument(0));

        DailyStats stats = statsSnapshotService.buildSnapshot(DATE);

        assertThat(stats.getStatDate()).isEqualTo(DATE);
        assertThat(stats.getLoginSuccessCount()).isEqualTo(11L);
        assertThat(stats.getLoginFailCount()).isEqualTo(22L);
        assertThat(stats.getLogoutCount()).isEqualTo(33L);
        assertThat(stats.getSignupCount()).isEqualTo(44L);
        assertThat(stats.getActiveUserCount()).isEqualTo(55L);
    }

    @Test
    @DisplayName("하루 경계는 당일 00:00 포함 ~ 익일 00:00 미포함으로 조회한다")
    void queriesHalfOpenDayRange() {
        givenCounts(1L, 2L, 3L, 4L, 5L);
        given(dailyStatsRepository.findByStatDate(DATE)).willReturn(Optional.empty());
        given(dailyStatsRepository.save(any(DailyStats.class))).willAnswer(inv -> inv.getArgument(0));

        statsSnapshotService.buildSnapshot(DATE);

        verify(authEventRepository).countByTypeBetween(AuthEventType.LOGIN_SUCCESS, START, END);
        verify(userRepository).countSignupsBetween(START, END);
        verify(authEventRepository).countDistinctUsersByTypeBetween(AuthEventType.LOGIN_SUCCESS, START, END);
    }

    @Test
    @DisplayName("DAU 는 로그인 성공 건수가 아니라 고유 사용자 수로 집계한다")
    void countsDauAsDistinctUsers() {
        // 한 사람이 여러 번 로그인한 상황: 성공 100건이지만 고유 사용자는 7명
        givenCounts(100L, 0L, 0L, 0L, 7L);
        given(dailyStatsRepository.findByStatDate(DATE)).willReturn(Optional.empty());
        given(dailyStatsRepository.save(any(DailyStats.class))).willAnswer(inv -> inv.getArgument(0));

        DailyStats stats = statsSnapshotService.buildSnapshot(DATE);

        assertThat(stats.getLoginSuccessCount()).isEqualTo(100L);
        assertThat(stats.getActiveUserCount()).isEqualTo(7L);
    }

    @Test
    @DisplayName("같은 일자를 다시 집계하면 기존 행을 덮어쓴다 - 백필이 unique 제약에 걸리면 안 된다")
    void reaggregationOverwritesExistingRow() {
        DailyStats existing = DailyStats.of(DATE);
        existing.updateCounts(1L, 1L, 1L, 1L, 1L);
        givenCounts(11L, 22L, 33L, 44L, 55L);
        given(dailyStatsRepository.findByStatDate(DATE)).willReturn(Optional.of(existing));
        given(dailyStatsRepository.save(any(DailyStats.class))).willAnswer(inv -> inv.getArgument(0));

        statsSnapshotService.buildSnapshot(DATE);

        ArgumentCaptor<DailyStats> saved = ArgumentCaptor.forClass(DailyStats.class);
        verify(dailyStatsRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing); // 새 행이 아니라 조회된 그 행
        assertThat(saved.getValue().getLoginSuccessCount()).isEqualTo(11L);
        assertThat(saved.getValue().getActiveUserCount()).isEqualTo(55L);
    }

    @Test
    @DisplayName("이벤트가 하나도 없는 날도 0 으로 채운 스냅샷을 남긴다")
    void savesZeroSnapshotForQuietDay() {
        givenCounts(0L, 0L, 0L, 0L, 0L);
        given(dailyStatsRepository.findByStatDate(DATE)).willReturn(Optional.empty());
        given(dailyStatsRepository.save(any(DailyStats.class))).willAnswer(inv -> inv.getArgument(0));

        DailyStats stats = statsSnapshotService.buildSnapshot(DATE);

        assertThat(stats.getLoginSuccessCount()).isZero();
        assertThat(stats.getActiveUserCount()).isZero();
        verify(dailyStatsRepository).save(any(DailyStats.class));
    }

    @Test
    @DisplayName("스케줄러는 오늘이 아니라 전일(KST)을 집계한다")
    void scheduledJobAggregatesYesterdayInKst() {
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        LocalDateTime start = yesterday.atStartOfDay();
        LocalDateTime end = yesterday.plusDays(1).atStartOfDay();
        given(authEventRepository.countByTypeBetween(any(), eq(start), eq(end))).willReturn(1L);
        given(userRepository.countSignupsBetween(start, end)).willReturn(1L);
        given(authEventRepository.countDistinctUsersByTypeBetween(any(), eq(start), eq(end))).willReturn(1L);
        given(dailyStatsRepository.findByStatDate(yesterday)).willReturn(Optional.empty());
        given(dailyStatsRepository.save(any(DailyStats.class))).willAnswer(inv -> inv.getArgument(0));

        statsSnapshotService.snapshotYesterday();

        verify(dailyStatsRepository).findByStatDate(yesterday);
        verify(dailyStatsRepository, never()).findByStatDate(yesterday.plusDays(1));
    }
}
