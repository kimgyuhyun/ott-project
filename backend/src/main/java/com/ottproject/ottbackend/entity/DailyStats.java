package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 일일 통계 스냅샷 엔티티
 *
 * 큰 흐름
 * - 인증 이벤트(auth_events)와 가입 정보 등을 하루 단위로 집계해 1행으로 보관한다.
 * - 매일 새벽 스케줄러가 전일자 스냅샷을 생성/갱신한다(재집계 가능하도록 멱등 처리).
 * - 관리자 통계 화면은 이 테이블만 조회하면 되므로 raw 이벤트 풀스캔을 피할 수 있다.
 *
 * 필드 개요
 * - statDate: 통계 기준 일자(고유)
 * - loginSuccessCount/loginFailCount/logoutCount: 인증 행위별 건수
 * - signupCount: 신규 가입자 수
 * - activeUserCount: 일일 활성 사용자 수(DAU, 로그인 성공 고유 사용자)
 * - createdAt/updatedAt: 생성/갱신 시각
 */
@Entity
@Table(name = "daily_stats", uniqueConstraints = {
        @UniqueConstraint(name = "ux_daily_stats_date", columnNames = "stat_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK

    @Column(name = "stat_date", nullable = false, unique = true)
    private LocalDate statDate; // 통계 기준 일자(고유)

    @Column(name = "login_success_count", nullable = false)
    private long loginSuccessCount; // 로그인 성공 건수

    @Column(name = "login_fail_count", nullable = false)
    private long loginFailCount; // 로그인 실패 건수

    @Column(name = "logout_count", nullable = false)
    private long logoutCount; // 로그아웃 건수

    @Column(name = "signup_count", nullable = false)
    private long signupCount; // 신규 가입자 수

    @Column(name = "active_user_count", nullable = false)
    private long activeUserCount; // DAU(로그인 성공 고유 사용자 수)

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt; // 최초 생성 시각

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt; // 마지막 갱신 시각

    // ===== 정적 팩토리 / 도메인 메서드 =====
    /**
     * 빈 스냅샷 생성(해당 일자의 신규 행)
     *
     * @param statDate 통계 기준 일자
     * @return 0으로 초기화된 DailyStats
     */
    public static DailyStats of(LocalDate statDate) {
        DailyStats s = new DailyStats();
        s.statDate = statDate;
        s.loginSuccessCount = 0;
        s.loginFailCount = 0;
        s.logoutCount = 0;
        s.signupCount = 0;
        s.activeUserCount = 0;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        s.createdAt = now;
        s.updatedAt = now;
        return s;
    }

    /**
     * 집계값 갱신(재집계 시에도 동일 행을 덮어써 멱등성을 보장)
     *
     * @param loginSuccessCount 로그인 성공 건수
     * @param loginFailCount 로그인 실패 건수
     * @param logoutCount 로그아웃 건수
     * @param signupCount 신규 가입자 수
     * @param activeUserCount DAU
     */
    public void updateCounts(long loginSuccessCount, long loginFailCount, long logoutCount,
                             long signupCount, long activeUserCount) {
        this.loginSuccessCount = loginSuccessCount;
        this.loginFailCount = loginFailCount;
        this.logoutCount = logoutCount;
        this.signupCount = signupCount;
        this.activeUserCount = activeUserCount;
        this.updatedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
