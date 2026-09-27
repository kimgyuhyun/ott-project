package com.ottproject.ottbackend.util;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * RedisCounterUtil
 *
 * 큰 흐름
 * - 시도 횟수 카운터를 1 올리고, 카운터가 새로 생길 때만 만료를 건다. 둘을 Lua 스크립트 하나로 실행한다.
 *
 * 왜 스크립트인가
 * - INCR 과 EXPIRE 를 따로 부르면 둘 사이에서 실패했을 때(타임아웃, 재시작) 만료 없는 카운터가 남는다.
 *   잠금 판정에 쓰는 카운터라면 그 대상이 영구히 잠긴다.
 * - 만료를 첫 증가에만 거는 것은 고정 윈도우다. 시도할 때마다 늘리면 공격이 이어지는 동안 잠금이 풀리지 않는다.
 *
 * 메서드 개요
 * - incrementWithTtl: 1 올린 뒤의 값을 돌려준다
 */
public final class RedisCounterUtil {
    private RedisCounterUtil() {} // 인스턴스화 방지

    private static final RedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]) "
                    + "if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return n",
            Long.class);

    /**
     * 카운터를 1 올리고 올린 뒤의 값을 돌려준다. 키가 새로 생길 때만 ttl 을 건다.
     * null 은 파이프라인·트랜잭션 안에서 실행했을 때만 나온다.
     */
    public static Long incrementWithTtl(StringRedisTemplate redisTemplate, String key, Duration ttl) {
        return redisTemplate.execute(INCREMENT_WITH_TTL, List.of(key), String.valueOf(ttl.toSeconds()));
    }
}
