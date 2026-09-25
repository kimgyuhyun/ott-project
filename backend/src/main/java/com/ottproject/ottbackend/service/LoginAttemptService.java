package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.util.RedisCounterUtil;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * LoginAttemptService
 *
 * 큰 흐름
 * - 이메일 단위로 로그인 실패 횟수를 Redis 카운터로 누적해, 임계치 초과 시 일정 시간 로그인을 잠근다.
 * - 무차별 대입(brute-force) 공격을 완화한다.
 * - 카운터에는 TTL(잠금 시간)을 부여하므로, 잠금 시간이 지나면 자동으로 풀린다.
 *
 * 동작 정책
 * - 비밀번호를 비교하기 전에 시도 1건을 원자적으로 센다(tryAcquireAttempt). 센 순번이 임계치를 넘으면 비교하지 않는다.
 *   비교한 뒤에 세면 동시에 들어온 요청이 모두 비교까지 가서 상한이 동시 요청 수만큼 늘어난다(LoginAttemptLimitTest).
 * - 최초 시도(카운터 생성)에만 TTL(잠금 시간)을 건다 → 고정 윈도우. 증가와 TTL 은 한 번에 실행한다(RedisCounterUtil).
 * - 카운터 ≥ 임계치 → 잠금 상태로 판단(로그인 거부)
 * - 실패한 시도는 센 그대로 남고, 로그인 성공 시 카운터 삭제(초기화)
 *
 * 설정(application.yml)
 * - app.login.max-fail-attempts: 잠금 임계치(기본 5)
 * - app.login.lock-minutes: 잠금 지속 시간(분, 기본 15)
 *
 * 메서드 개요
 * - isBlocked: 현재 잠금 상태 여부
 * - tryAcquireAttempt: 비밀번호 비교 전에 시도 1건을 센다(임계치 이내면 true)
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
     * 비밀번호를 비교하기 전에 시도 1건을 센다.
     * - 원자적으로 센 순번이 임계치 이하일 때만 비교를 허용한다. 동시에 들어온 요청도 서로 다른 순번을 받으므로
     *   비교는 잠금 시간 동안 임계치만큼만 일어난다.
     * - 최초 시도에만 TTL(잠금 시간)을 건다. 증가와 TTL 을 따로 부르면 그 사이의 실패로 TTL 없는 카운터가 남아
     *   계정이 영구히 잠긴다(잠금 확인이 로그인보다 먼저라 성공으로도 풀리지 않는다).
     *
     * @param email 대상 이메일
     * @return 비밀번호를 비교해도 되면 true, 임계치를 넘은 시도면 false
     */
    public boolean tryAcquireAttempt(String email) {
        if (email == null || email.isBlank()) {
            return true; // 다른 메서드와 같이 빈 이메일은 세지 않는다(로그인 DTO 검증이 먼저 막는다)
        }
        Long count = RedisCounterUtil.incrementWithTtl(redisTemplate, key(email), Duration.ofMinutes(lockMinutes));
        if (count == null) {
            return false; // 셀 수 없으면 비교하지 않는다(fail-closed)
        }
        if (count == maxFailAttempts) {
            // 이메일은 남기지 않는다(PLATFORM 9: 로그의 사용자 식별자는 이메일이 아니다). 요청은 상관관계 ID 로 찾는다.
            log.warn("로그인 시도가 임계치에 도달 - 이번 시도가 실패하면 {}분간 잠긴다", lockMinutes);
        }
        return count <= maxFailAttempts;
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
