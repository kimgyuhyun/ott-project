package com.ottproject.ottbackend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Jikan 서킷 브레이커의 check-then-act 경쟁 조건 검증 (#162)
 *
 * 결함
 * - recordFailure 가 연속 실패 카운터를 락 밖에서 올려 지역 변수에 담고, 락 안에서 그 값으로 열지 판정했다.
 * - 그 사이 recordSuccess 가 카운터를 0 으로, circuitOpen 을 false 로 되돌리면 실패 쪽은 낡은 값 5 로 서킷을 연다.
 * - 결과 "카운터 0 + 열림" 은 두 호출을 어느 순서로 통째로 실행해도 나오지 않는 상태다.
 *   열리면 5분 동안 Jikan 을 부르지 않고 null 을 돌려준다.
 *
 * 순서를 강제하는 방법
 * - 테스트 스레드가 circuitLock 을 먼저 쥐고, 실패 스레드가 바로 그 락 앞에서 BLOCKED 가 될 때까지 기다린다.
 * - 락을 쥔 채로(재진입) recordSuccess 를 끝까지 실행한 뒤 놓는다. 그러면 실패 쪽 판정이 반드시 성공 뒤에 온다.
 * - 스레드 여러 개를 경쟁시키는 방식은 틈이 좁아 재현이 들쭉날쭉하므로 쓰지 않는다.
 */
class SimpleJikanCircuitBreakerRaceTest {

    @Test
    @DisplayName("실패 판정이 락을 기다리는 사이 성공이 기록되면 서킷을 열지 않는다")
    void staleFailureCountDoesNotOpenCircuitAfterSuccess() throws Exception {
        SimpleJikanApiService service = new SimpleJikanApiService(new RestTemplate());
        AtomicInteger failures = (AtomicInteger) ReflectionTestUtils.getField(service, "consecutiveFailures");
        Object circuitLock = ReflectionTestUtils.getField(service, "circuitLock");
        failures.set(4); // 다음 실패가 임계값 5 를 채운다

        Thread failure = new Thread(() -> ReflectionTestUtils.invokeMethod(service, "recordFailure"));
        synchronized (circuitLock) {
            failure.start();
            awaitBlockedOn(failure, circuitLock);
            ReflectionTestUtils.invokeMethod(service, "recordSuccess");
        }
        failure.join(5_000);

        boolean open = (boolean) ReflectionTestUtils.getField(service, "circuitOpen");
        assertThat(open)
                .as("consecutiveFailures=%d 인데 circuitOpen=%s", failures.get(), open)
                .isFalse();
        assertThat(failures.get()).as("성공 뒤의 실패 1회만 남아야 한다").isEqualTo(1);
    }

    // 다른 모니터(클래스 초기화 등)에서 잠깐 BLOCKED 된 것을 circuitLock 대기로 오인하지 않도록 락 정체까지 본다.
    private static void awaitBlockedOn(Thread thread, Object lock) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(thread.threadId());
            if (info != null
                    && info.getThreadState() == Thread.State.BLOCKED
                    && info.getLockInfo() != null
                    && info.getLockInfo().getIdentityHashCode() == System.identityHashCode(lock)) {
                return;
            }
            Thread.sleep(1);
        }
        throw new IllegalStateException("실패 스레드가 circuitLock 앞에서 대기하지 않았다: " + thread.getState());
    }
}
