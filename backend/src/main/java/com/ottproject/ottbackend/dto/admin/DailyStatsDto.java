package com.ottproject.ottbackend.dto.admin;

import com.ottproject.ottbackend.entity.DailyStats;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 관리자 일일 통계 응답 항목
 *
 * 큰 흐름
 * - 미리 집계된 DailyStats 스냅샷을 관리자 통계 화면에 내보낸다.
 *
 * 왜 엔티티를 그대로 쓰지 않는가
 * - 응답 본문에 엔티티를 쓰면 컬럼을 추가하는 순간 API 계약이 같이 바뀐다.
 * - id 는 화면이 쓰지 않는 내부 식별자라 담지 않는다. 행의 정체성은 statDate 가 갖는다.
 *
 * 필드 개요
 * - statDate: 통계 기준 일자
 * - loginSuccessCount/loginFailCount/logoutCount: 인증 행위별 건수
 * - signupCount: 신규 가입자 수
 * - activeUserCount: DAU(로그인 성공 고유 사용자)
 * - updatedAt: 마지막 재집계 시각(백필 여부 판단용)
 */
@Getter
@Builder
@AllArgsConstructor
public class DailyStatsDto {

    private final LocalDate statDate;
    private final long loginSuccessCount;
    private final long loginFailCount;
    private final long logoutCount;
    private final long signupCount;
    private final long activeUserCount;
    private final LocalDateTime updatedAt;

    /**
     * 엔티티 → DTO 변환.
     *
     * @param stats 일일 통계 스냅샷
     * @return 응답용 DTO
     */
    public static DailyStatsDto from(DailyStats stats) {
        return DailyStatsDto.builder()
                .statDate(stats.getStatDate())
                .loginSuccessCount(stats.getLoginSuccessCount())
                .loginFailCount(stats.getLoginFailCount())
                .logoutCount(stats.getLogoutCount())
                .signupCount(stats.getSignupCount())
                .activeUserCount(stats.getActiveUserCount())
                .updatedAt(stats.getUpdatedAt())
                .build();
    }
}
