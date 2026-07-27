package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.EpisodeProgressFlushDto;
import com.ottproject.ottbackend.mybatis.PlayerProgressQueryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ProgressBufferService 검증
 *
 * 왜 이 테스트가 필요한가
 * - 버퍼 값 형식("위치:길이:기록시각")은 쓰기와 flush 가 암묵적으로 공유하는 계약이다. 깨지면 진행률이 통째로 유실된다.
 * - flush 는 버퍼를 rename 으로 들어낸다. 이 순서가 무너지면 반영 중에 들어온 쓰기가 사라진다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProgressBufferServiceTest {

    private static final String BUFFER_KEY = "ott:progress-buffer:v1";
    private static final String FLUSHING_KEY = "ott:progress-buffer:v1:flushing";

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private PlayerProgressQueryMapper progressQueryMapper;
    @Mock private HashOperations<String, Object, Object> hashOps;

    @InjectMocks private ProgressBufferService service;

    @BeforeEach
    void setUp() {
        doReturn(hashOps).when(redisTemplate).opsForHash();
    }

    @Test
    @DisplayName("쓰기는 Redis 해시에만 남기고 DB 를 건드리지 않는다")
    void writeGoesToRedisOnly() {
        service.write(1L, 10L, 500, 1400);

        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(hashOps).put(org.mockito.ArgumentMatchers.eq(BUFFER_KEY),
                org.mockito.ArgumentMatchers.eq("1:10"), value.capture());
        assertThat(value.getValue().toString()).startsWith("500:1400:");
        verify(progressQueryMapper, never()).upsertProgressBatch(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("버퍼가 비어 있으면 아무것도 하지 않는다")
    void flushSkipsWhenEmpty() {
        given(redisTemplate.hasKey(FLUSHING_KEY)).willReturn(false);
        given(redisTemplate.hasKey(BUFFER_KEY)).willReturn(false);

        service.flush();

        verify(progressQueryMapper, never()).upsertProgressBatch(org.mockito.ArgumentMatchers.anyList());
        verify(redisTemplate, never()).rename(BUFFER_KEY, FLUSHING_KEY);
    }

    @Test
    @DisplayName("버퍼를 rename 으로 들어낸 뒤 배치 반영하고 스냅샷만 지운다")
    void flushDrainsAndUpserts() {
        given(redisTemplate.hasKey(FLUSHING_KEY)).willReturn(false);
        given(redisTemplate.hasKey(BUFFER_KEY)).willReturn(true);
        given(hashOps.entries(FLUSHING_KEY)).willReturn(Map.of("1:10", "500:1400:1700000000000"));

        service.flush();

        verify(redisTemplate).rename(BUFFER_KEY, FLUSHING_KEY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EpisodeProgressFlushDto>> rows = ArgumentCaptor.forClass(List.class);
        verify(progressQueryMapper).upsertProgressBatch(rows.capture());
        EpisodeProgressFlushDto row = rows.getValue().get(0);
        assertThat(row.getUserId()).isEqualTo(1L);
        assertThat(row.getEpisodeId()).isEqualTo(10L);
        assertThat(row.getPositionSec()).isEqualTo(500);
        assertThat(row.getDurationSec()).isEqualTo(1400);
        assertThat(row.getUpdatedAt()).isNotNull();

        // 새로 들어온 쓰기가 담기는 BUFFER_KEY 는 건드리지 않는다
        verify(redisTemplate).delete(FLUSHING_KEY);
        verify(redisTemplate, never()).delete(BUFFER_KEY);
    }

    /**
     * 앞선 flush 가 DB 오류로 중단되면 스냅샷이 남는다. 다음 주기가 그것을 먼저 처리하지 않으면 그 구간이 영영 유실된다.
     */
    @Test
    @DisplayName("이전 flush 가 남긴 스냅샷이 있으면 rename 없이 그것부터 반영한다")
    void flushResumesLeftoverSnapshot() {
        given(redisTemplate.hasKey(FLUSHING_KEY)).willReturn(true);
        given(hashOps.entries(FLUSHING_KEY)).willReturn(Map.of("2:20", "10:100:1700000000000"));

        service.flush();

        verify(redisTemplate, never()).rename(BUFFER_KEY, FLUSHING_KEY);
        verify(progressQueryMapper).upsertProgressBatch(org.mockito.ArgumentMatchers.anyList());
    }
}
