package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.AuthEvent;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AuthEventType;
import com.ottproject.ottbackend.enums.AuthProvider;
import com.ottproject.ottbackend.repository.AuthEventRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuthEventService
 *
 * 큰 흐름
 * - 로그인/로그아웃/실패/탈퇴 등 인증 행위를 감사 로그(AuthEvent)로 적재한다.
 * - 동시에 별도의 파일 로거(AUTH_AUDIT)로도 남겨, DB 장애 시에도 로그 추적이 가능하도록 이중화한다.
 * - 사용자 응답 지연을 막기 위해 @Async 로 비동기 처리한다.
 *
 * 설계 메모
 * - HttpServletRequest 같은 요청 스코프 객체는 비동기 스레드에서 만료될 수 있으므로,
 *   호출 측(컨트롤러/핸들러)에서 IP/User-Agent/세션ID 등 원시 값을 추출해 넘겨받는다.
 *
 * 메서드 개요
 * - record: 인증 이벤트 1건을 비동기로 적재 + 감사 로그 기록
 */
@Service
@RequiredArgsConstructor
public class AuthEventService {

    // 인증 전용 감사 로거 (logback-spring.xml 의 AUTH_AUDIT 로거로 라우팅되어 auth-audit.log 에 적재됨)
    private static final Logger auditLog = LoggerFactory.getLogger("AUTH_AUDIT");

    private final AuthEventRepository authEventRepository; // 인증 이벤트 적재
    private final UserRepository userRepository; // 이메일 → 사용자 ID 매핑용

    /**
     * 인증 이벤트 1건을 비동기로 적재한다.
     * - 실패 이벤트(LOGIN_FAIL)는 존재하지 않는 계정일 수 있어 userId 가 null 일 수 있다.
     * - 기록 자체가 실패해도 본 흐름(로그인/로그아웃)에는 영향을 주지 않도록 예외를 삼킨다.
     *
     * @param type 이벤트 유형
     * @param provider 인증 제공자(LOCAL/GOOGLE/NAVER/KAKAO)
     * @param email 시도 이메일
     * @param ip 클라이언트 IP
     * @param userAgent 클라이언트 User-Agent
     * @param sessionId 세션 ID
     * @param failReason 실패 사유(성공 시 null)
     */
    @Async
    @Transactional
    public void record(AuthEventType type, AuthProvider provider, String email,
                       String ip, String userAgent, String sessionId, String failReason) {
        try {
            Long userId = null;
            if (email != null && !email.isBlank()) {
                // 이메일로 사용자 식별 시도(없으면 null 유지 → 실패/미식별 케이스)
                userId = userRepository.findByEmail(email.trim().toLowerCase())
                        .map(User::getId)
                        .orElse(null);
            }

            AuthEvent event = AuthEvent.of(userId, email, type, provider, ip, userAgent, sessionId, failReason);
            authEventRepository.save(event); // DB 적재

            // 파일 감사 로그 이중화 (DB 와 별개로 추적 가능)
            auditLog.info("event={} provider={} email={} userId={} ip={} sessionId={} reason={}",
                    type, provider, email, userId, ip, sessionId, failReason);
        } catch (Exception ex) {
            // 감사 로그 적재 실패가 인증 흐름을 깨지 않도록 방어
            auditLog.error("인증 이벤트 기록 실패: type={}, email={}, error={}", type, email, ex.getMessage(), ex);
        }
    }
}
