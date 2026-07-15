package com.ottproject.ottbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * LoginAttemptService
 *
 * 큰 흐름
 * - 이메일 단위로 로그인 실패 횟수를 Redis 카운터로 누적해, 임계치 초과 시 일정 시간 로그인을 잠근다.
 * - 무차별 대입(brute-force) 공격을 완화한다.
 * - 카운터에는 TTL(잠금 시간)을 부여하므로, 잠금 시간이 지나면 자동으로 풀린다.
 *
 * 동작 정책
 * - 실패 시 카운터 +1, 최초 실패 시 TTL(잠금 시간) 설정
 * - 카운터 ≥ 임계치 → 잠금 상태로 판단(로그인 거부)
 * - 로그인 성공 시 카운터 삭제(초기화)
 *
 * 설정(application.yml)
 * - app.login.max-fail-attempts: 잠금 임계치(기본 5)
 * - app.login.lock-minutes: 잠금 지속 시간(분, 기본 15)
 *
 * 메서드 개요
 * - isBlocked: 현재 잠금 상태 여부
 * - recordFailure: 실패 1건 누적(누적 후 횟수 반환)
 * - reset: 카운터 초기화(로그인 성공 시)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private static final String KEY_PREFIX = "ott:login-fail:v1:"; // 실패 카운터 키 프리픽스

    private final StringRedisTemplate redisTemplate; // 문자열 기반 Redis 템플릿

    @Value("${app.login.max-fail-attempts:5}")
    private int maxFailAttempts; // 잠금 임계치(기본 5회)

    @Value("${app.login.lock-minutes:15}")
    private long lockMinutes; // 잠금 지속 시간(기본 15분)

    @Value("${app.login.turnstile-after-fails:1}")
    private int turnstileAfterFails; // 이 횟수 이상 실패 시 Turnstile(사람 확인) 요구(기본 1회)

    /**
     * 현재 잠금 상태 여부
     *
     * @param email 대상 이메일
     * @return 임계치 이상 실패가 누적되어 잠긴 경우 true
     */
    public boolean isBlocked(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String value = redisTemplate.opsForValue().get(key(email));
        if (value == null) {
            return false;
        }
        try {
            return Long.parseLong(value) >= maxFailAttempts;
        } catch (NumberFormatException e) {
            return false; // 비정상 값은 잠금으로 보지 않음
        }
    }

    /**
     * Turnstile(사람 확인) 요구 여부
     * - 직전까지 누적된 로그인 실패가 임계치 이상이면 다음 로그인 시 사람 확인을 요구한다.
     * - 큰 사이트들이 쓰는 "정상 첫 로그인은 통과, 실패하면 그때부터 캡차" 방식.
     *
     * @param email 대상 이메일
     * @return 사람 확인이 필요하면 true
     */
    public boolean isChallengeRequired(String email) {
        return getFailCount(email) >= turnstileAfterFails;
    }

    /**
     * 현재 누적된 로그인 실패 횟수(없으면 0)
     *
     * @param email 대상 이메일
     * @return 누적 실패 횟수
     */
    public long getFailCount(String email) {
        if (email == null || email.isBlank()) {
            return 0L;
        }
        String value = redisTemplate.opsForValue().get(key(email));
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L; // 비정상 값은 0으로 취급
        }
    }

    /**
     * 로그인 실패 1건을 누적한다.
     * - 최초 실패(카운터 생성) 시점에 TTL(잠금 시간)을 설정한다.
     *
     * @param email 대상 이메일
     * @return 누적된 실패 횟수
     */
    public long recordFailure(String email) {
        if (email == null || email.isBlank()) {
            return 0L;
        }
        String key = key(email);
        Long count = redisTemplate.opsForValue().increment(key); // 원자적 +1 (없으면 1로 생성)
        if (count != null && count == 1L) {
            // 최초 실패 시에만 만료 시간 설정 → 잠금 시간 동안 슬라이딩 없이 고정 윈도우 유지
            redisTemplate.expire(key, Duration.ofMinutes(lockMinutes));
        }
        long result = count != null ? count : 0L;
        if (result >= maxFailAttempts) {
            log.warn("로그인 실패 임계치 도달로 계정 잠금 - email={}, count={}, lockMinutes={}", email, result, lockMinutes);
        }
        return result;
    }

    /**
     * 카운터 초기화(로그인 성공 시 호출)
     *
     * @param email 대상 이메일
     */
    public void reset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        redisTemplate.delete(key(email));
    }

    /**
     * 이메일 → Redis 키 (저장 정책과 동일하게 소문자로 정규화)
     */
    private String key(String email) {
        return KEY_PREFIX + email.trim().toLowerCase();
    }
}
