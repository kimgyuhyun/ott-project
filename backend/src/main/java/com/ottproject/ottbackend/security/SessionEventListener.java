package com.ottproject.ottbackend.security;

import com.ottproject.ottbackend.enums.AuthEventType;
import com.ottproject.ottbackend.service.AuthEventService;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * SessionEventListener
 *
 * 큰 흐름
 * - 세션이 소멸될 때(타임아웃/브라우저 종료 등) SESSION_EXPIRED 감사 이벤트를 기록한다.
 * - 명시적 로그아웃/탈퇴는 컨트롤러에서 이미 LOGOUT/WITHDRAW 로 기록하므로,
 *   해당 흐름에서 세션에 심어둔 플래그(EXPLICIT_INVALIDATION_FLAG)를 보고 중복 기록을 건너뛴다.
 * - 이로써 "그냥 창을 닫은" 사용자의 접속 종료까지 통계에 포착할 수 있다.
 *
 * 참고
 * - HttpSessionListener 빈은 스프링 부트가 내장 컨테이너에 자동 등록한다.
 * - 세션 소멸은 요청 스코프가 아닌 컨테이너 스레드에서 발생할 수 있어 IP/User-Agent 는 알 수 없다.
 */
@Component
@RequiredArgsConstructor
public class SessionEventListener implements HttpSessionListener {

    // 명시적 로그아웃/탈퇴 시 컨트롤러가 세션에 심는 플래그 키 (중복 기록 방지)
    public static final String EXPLICIT_INVALIDATION_FLAG = "authEventRecorded";

    private final AuthEventService authEventService; // 감사 로그 기록 주입

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        var session = se.getSession();

        // 명시적 로그아웃/탈퇴로 이미 기록된 세션이면 건너뜀
        Object alreadyRecorded = session.getAttribute(EXPLICIT_INVALIDATION_FLAG);
        if (Boolean.TRUE.equals(alreadyRecorded)) {
            return;
        }

        // 로그인 상태였던 세션만(이메일 보유) 만료 이벤트로 기록
        Object emailObj = session.getAttribute("userEmail");
        if (emailObj instanceof String email && !email.isBlank()) {
            // 세션 소멸 컨텍스트라 IP/User-Agent 는 없음(null), 세션ID 만 남긴다
            authEventService.record(AuthEventType.SESSION_EXPIRED, null, email,
                    null, null, session.getId(), null);
        }
    }
}
