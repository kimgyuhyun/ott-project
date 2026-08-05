package com.ottproject.ottbackend.dto.admin;

import com.ottproject.ottbackend.entity.AuthEvent;
import com.ottproject.ottbackend.enums.AuthEventType;
import com.ottproject.ottbackend.enums.AuthProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 관리자 인증 이벤트(감사 로그) 응답 항목
 *
 * 큰 흐름
 * - 최근 인증 행위를 관리자 화면에 시간순으로 내보낸다.
 *
 * 세션 식별자를 원문으로 내보내지 않는 이유
 * - sessionId 는 그 자체가 인증 수단의 식별자다. 감사 테이블에 보관하는 것과
 *   API 응답으로 흘려보내는 것은 노출 범위가 다르다.
 * - 다만 "같은 세션에서 일어난 일들"을 화면에서 묶어 보는 용도는 살려야 하므로,
 *   앞 8자만 남긴 접두사를 준다. 서로 다른 세션을 구분하기에는 충분하고
 *   그 값으로 세션을 흉내 낼 수는 없다.
 *
 * 필드 개요
 * - occurredAt/eventType/provider: 무엇이 언제 일어났는가
 * - userId/email: 누가(실패·미식별 시 userId 는 null)
 * - ipAddress/userAgent: 어디서
 * - sessionIdPrefix: 같은 세션 묶음 식별용 접두사
 * - failReason: 실패 사유(성공 시 null)
 */
@Getter
@Builder
@AllArgsConstructor
public class AuthEventDto {

    private final Long id;
    private final Long userId;
    private final String email;
    private final AuthEventType eventType;
    private final AuthProvider provider;
    private final String ipAddress;
    private final String userAgent;
    private final String sessionIdPrefix;
    private final String failReason;
    private final LocalDateTime occurredAt;

    /** 세션 식별자에서 노출을 허용하는 접두사 길이. */
    private static final int SESSION_ID_PREFIX_LENGTH = 8;

    /**
     * 엔티티 → DTO 변환. sessionId 는 접두사만 남긴다.
     *
     * @param event 인증 이벤트
     * @return 응답용 DTO
     */
    public static AuthEventDto from(AuthEvent event) {
        return AuthEventDto.builder()
                .id(event.getId())
                .userId(event.getUserId())
                .email(event.getEmail())
                .eventType(event.getEventType())
                .provider(event.getProvider())
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .sessionIdPrefix(maskSessionId(event.getSessionId()))
                .failReason(event.getFailReason())
                .occurredAt(event.getOccurredAt())
                .build();
    }

    /**
     * 세션 식별자를 앞 8자로 자른다. 짧거나 없으면 그대로 둔다(자를 것이 없다).
     */
    private static String maskSessionId(String sessionId) {
        if (sessionId == null || sessionId.length() <= SESSION_ID_PREFIX_LENGTH) {
            return sessionId;
        }
        return sessionId.substring(0, SESSION_ID_PREFIX_LENGTH);
    }
}
