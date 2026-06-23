package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.AuthEventType;
import com.ottproject.ottbackend.enums.AuthProvider;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 인증 이벤트(감사 로그) 엔티티
 *
 * 큰 흐름
 * - 로그인/로그아웃/로그인 실패/탈퇴 등 인증 행위를 한 건씩 적재한다.
 * - 이 raw 데이터를 일 단위로 집계해 통계 스냅샷(DailyStats)을 만든다.
 * - 보안 추적(누가/언제/어디서)을 위해 IP/User-Agent/세션ID 를 함께 보관한다.
 *
 * 필드 개요
 * - userId: 행위 사용자 ID(실패/미식별 시 null 가능)
 * - email: 시도 이메일(실패 분석용으로 항상 보관)
 * - eventType: 이벤트 유형(LOGIN_SUCCESS/LOGIN_FAIL/LOGOUT/WITHDRAW)
 * - provider: 인증 제공자(LOCAL/GOOGLE/NAVER/KAKAO)
 * - ipAddress/userAgent/sessionId: 접속 메타데이터
 * - failReason: 실패 사유(성공 시 null)
 * - occurredAt: 발생 시각(KST)
 */
@Entity
@Table(name = "auth_events", indexes = {
        @Index(name = "idx_auth_events_occurred", columnList = "occurred_at"),
        @Index(name = "idx_auth_events_type_occurred", columnList = "event_type, occurred_at"),
        @Index(name = "idx_auth_events_user", columnList = "user_id, occurred_at")
})
@Getter
@Setter
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 모든 필드 생성자
public class AuthEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK (DB 자동 생성)

    @Column(name = "user_id")
    private Long userId; // 행위 사용자 ID (실패/미식별 시 null) - FK 강제 대신 느슨한 참조

    @Column(length = 255)
    private String email; // 시도 이메일 (실패 분석을 위해 항상 기록)

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private AuthEventType eventType; // 이벤트 유형

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AuthProvider provider; // 인증 제공자 (소셜 실패 등에서 null 가능)

    @Column(name = "ip_address", length = 45)
    private String ipAddress; // 접속 IP (IPv6 고려해 45자)

    @Column(name = "user_agent", length = 512)
    private String userAgent; // 접속 User-Agent

    @Column(name = "session_id", length = 128)
    private String sessionId; // 세션 ID

    @Column(name = "fail_reason", length = 255)
    private String failReason; // 실패 사유 (성공 시 null)

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt; // 발생 시각 (KST)

    // ===== 정적 팩토리 메서드 =====
    /**
     * 인증 이벤트 생성
     * - 발생 시각은 서비스 정책(KST)에 맞춰 생성 시점에 고정한다.
     *
     * @param userId 사용자 ID(없으면 null)
     * @param email 시도 이메일
     * @param eventType 이벤트 유형
     * @param provider 인증 제공자
     * @param ipAddress 접속 IP
     * @param userAgent 접속 User-Agent
     * @param sessionId 세션 ID
     * @param failReason 실패 사유(성공 시 null)
     * @return 생성된 AuthEvent 엔티티
     */
    public static AuthEvent of(Long userId, String email, AuthEventType eventType, AuthProvider provider,
                               String ipAddress, String userAgent, String sessionId, String failReason) {
        AuthEvent e = new AuthEvent();
        e.userId = userId;
        e.email = email != null ? email.trim().toLowerCase() : null; // 사용자 이메일 저장 정책(소문자)과 정합성 유지
        e.eventType = eventType;
        e.provider = provider;
        e.ipAddress = ipAddress;
        e.userAgent = userAgent;
        e.sessionId = sessionId;
        e.failReason = failReason;
        e.occurredAt = LocalDateTime.now(ZoneId.of("Asia/Seoul")); // 프로젝트 표준 시간대(KST)
        return e;
    }
}
