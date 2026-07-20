package com.ottproject.ottbackend.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * ShedLock 설정 (스케줄러 분산락)
 *
 * 큰 흐름
 * - @Scheduled 배치가 여러 인스턴스에서 동시에 도는 것을 막는다.
 * - 락 저장소는 Redis(세션 저장소와 동일 인스턴스, 키 접두어만 분리).
 * - 실행 직전 락 획득을 시도해 성공한 인스턴스만 실행하고, 실패한 쪽은 조용히 넘어간다.
 *
 * 설계 메모
 * - "1번 인스턴스만 스케줄러 켜기" 같은 정적 지정과 달리 리더를 미리 못 박지 않는다.
 *   매 실행마다 먼저 락을 잡은 쪽이 수행하므로, 특정 인스턴스가 죽어도 배치가 멈추지 않는다.
 * - defaultLockAtMostFor 는 락 보유 시간 상한이다. 락을 잡은 인스턴스가 응답 없이 죽어도
 *   이 시간이 지나면 락이 풀려 다른 인스턴스가 이어받는다(영구 교착 방지).
 *   개별 스케줄에서 @SchedulerLock(lockAtMostFor=...) 로 덮어쓸 수 있다.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory redisConnectionFactory) {
        // 두 번째 인자는 키 접두어(환경 구분자). 세션 키(ott:session:*)와 섞이지 않게 분리한다.
        return new RedisLockProvider(redisConnectionFactory, "ott");
    }
}
