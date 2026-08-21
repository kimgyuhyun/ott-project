package com.ottproject.ottbackend.security;

import java.time.Duration;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;

/**
 * UserSessionRegistry
 *
 * 큰 흐름
 * - 사용자 1명이 가진 세션 ID 들을 Redis Set 으로 들고 있다가, 탈퇴 시 한 번에 끊는다.
 *
 * 왜 필요한가
 * - 예전에는 요청마다 이메일로 사용자를 조회했고, 탈퇴가 이메일을 익명화하므로
 *   다른 기기의 세션도 다음 요청에서 조회 실패로 자연히 차단됐다.
 * - 지금은 세션에 담긴 userId/userRole 로 인증하므로 그 조회가 사라졌다. 그래서
 *   탈퇴한 사용자의 다른 기기 세션이 세션 만료(24시간)까지 살아남는다. 이 클래스가 그 구멍을 막는다.
 *
 * 왜 Spring Session 인덱스를 쓰지 않았나
 * - findByPrincipalName 은 RedisIndexedSessionRepository 에서만 제공된다. 그쪽으로 바꾸면
 *   Redis 키 구조와 keyspace notification 설정까지 함께 바뀌어 운영 중인 세션에 영향이 간다.
 *   탈퇴 하나 때문에 감수할 범위가 아니라, 인덱스를 직접 들고 있기로 했다.
 *
 * 메서드 개요
 * - register: 로그인 시 세션 ID 를 사용자 인덱스에 추가
 * - revokeOthers: 탈퇴 시 현재 세션을 제외한 나머지 세션 삭제
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSessionRegistry {

    private final StringRedisTemplate redisTemplate;
    private final SessionRepository<? extends Session> sessionRepository; // 세션 삭제는 Spring Session 공개 API 로만 한다

    private static final String KEY_PREFIX = "ott:user-sessions:v1:";
    // 세션 만료(application-*.yml 의 spring.session.timeout = 86400초)와 맞춘다.
    // 인덱스가 세션보다 오래 살아도 이득이 없고, 죽은 ID 만 쌓인다.
    private static final Duration TTL = Duration.ofDays(1);

    /**
     * 로그인 시 세션 ID 를 사용자 인덱스에 추가한다.
     * 실패해도 로그인 자체는 막지 않는다 — 인덱스는 탈퇴 시에만 쓰는 보조 자료다.
     */
    public void register(Long userId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        String key = key(userId);
        try {
            redisTemplate.opsForSet().add(key, sessionId);
            redisTemplate.expire(key, TTL); // 로그인할 때마다 갱신되어 활성 사용자의 인덱스는 유지된다
        } catch (Exception e) {
            log.error("[SessionRegistry] register 실패 userId={} error={}", userId, e.getMessage(), e);
        }
    }

    /**
     * 현재 세션을 제외한 해당 사용자의 모든 세션을 삭제한다.
     *
     * 현재 세션을 남기는 이유
     * - 호출부(탈퇴)가 감사 로그 플래그를 심고 session.invalidate() 로 직접 끊는다.
     *   여기서 미리 지우면 그 경로가 이미 사라진 세션을 다루게 된다.
     */
    public void revokeOthers(Long userId, String currentSessionId) {
        if (userId == null) {
            return;
        }
        String key = key(userId);
        try {
            Set<String> sessionIds = redisTemplate.opsForSet().members(key);
            if (sessionIds != null) {
                for (String sessionId : sessionIds) {
                    if (sessionId.equals(currentSessionId)) {
                        continue; // 현재 세션은 호출부가 끊는다
                    }
                    sessionRepository.deleteById(sessionId); // 이미 만료된 ID 여도 무해하다
                }
            }
            redisTemplate.delete(key); // 탈퇴했으므로 인덱스 자체를 버린다
        } catch (Exception e) {
            // 탈퇴(DB 익명화)는 이미 커밋됐다. 여기서 예외를 던지면 성공한 탈퇴가 실패로 보인다.
            log.error("[SessionRegistry] revokeOthers 실패 userId={} error={}", userId, e.getMessage(), e);
        }
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
