package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.EpisodeProgressFlushDto;
import com.ottproject.ottbackend.dto.EpisodeProgressResponseDto;
import com.ottproject.ottbackend.mybatis.PlayerProgressQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ProgressBufferService
 *
 * 큰 흐름
 * - 시청 진행률 쓰기를 Redis 버퍼에 받아 두고, 스케줄러가 주기적으로 DB 에 배치 반영한다(write-back).
 * - 요청 경로에서 DB 트랜잭션이 사라지므로 커넥션 점유가 없어진다.
 *
 * 왜 이 구조인가
 * - 진행률은 전체 트래픽의 대부분을 차지하는데 요청 1건마다 SELECT + UPDATE + 커밋을 했다.
 *   부하 테스트에서 커넥션 풀 20 개가 전부 고갈되고 스레드 170 여 개가 대기했다(loadtest/RESULTS.md).
 * - 시청 위치는 몇 초 어긋나도 되는 데이터라 마지막 flush 이후 구간의 유실을 감수할 수 있다.
 *   결제·주문처럼 유실이 불가한 데이터에는 쓸 수 없는 패턴이다.
 *
 * 유실·정합성 메모
 * - Redis 다운 또는 flush 전 종료 시 그 구간 진행률은 사라진다(최대 flush 주기만큼).
 * - DB 는 항상 최신이 아니다. 진행률을 DB 에서 직접 읽는 경로(시청 기록 등)는 flush 주기만큼 뒤처진다.
 * - 조회는 버퍼를 먼저 보고 없을 때만 DB 로 내려가므로 사용자 관점에서는 즉시 반영으로 보인다.
 *
 * 메서드 개요
 * - write/read/readAll/evict: 버퍼 입출력
 * - flush: 버퍼를 비우며 DB 배치 upsert
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressBufferService {

    private final StringRedisTemplate redisTemplate;
    private final PlayerProgressQueryMapper progressQueryMapper;

    // 쓰기가 들어오는 버퍼. flush 시작 시 FLUSHING_KEY 로 이름을 바꿔 통째로 들어낸다.
    private static final String BUFFER_KEY = "ott:progress-buffer:v1";
    // 반영 중인 스냅샷. 이름 변경 뒤 들어오는 쓰기는 새 BUFFER_KEY 로 가므로 유실되지 않는다.
    private static final String FLUSHING_KEY = "ott:progress-buffer:v1:flushing";
    private static final int CHUNK_SIZE = 500; // 한 문장에 넣을 행 수

    /**
     * 진행률 버퍼 기록(DB 접근 없음)
     */
    public void write(Long userId, Long episodeId, int positionSec, int durationSec) {
        redisTemplate.opsForHash().put(
                BUFFER_KEY,
                field(userId, episodeId),
                positionSec + ":" + durationSec + ":" + System.currentTimeMillis());
    }

    /**
     * 버퍼에 있는 진행률 조회(없으면 empty — 호출측이 DB 로 내려간다)
     */
    public Optional<EpisodeProgressResponseDto> read(Long userId, Long episodeId) {
        String f = field(userId, episodeId);
        Object v = redisTemplate.opsForHash().get(BUFFER_KEY, f);
        if (v == null) {
            v = redisTemplate.opsForHash().get(FLUSHING_KEY, f); // 반영 중인 스냅샷도 아직 DB 에 없을 수 있다
        }
        return v == null ? Optional.empty() : Optional.of(parse(v.toString()));
    }

    /**
     * 버퍼에 있는 진행률 일괄 조회(에피소드 ID 기준, 없는 것은 결과에서 빠진다)
     */
    public Map<Long, EpisodeProgressResponseDto> readAll(Long userId, Collection<Long> episodeIds) {
        List<Object> fields = episodeIds.stream().map(id -> (Object) field(userId, id)).toList();
        List<Long> ids = new ArrayList<>(episodeIds);

        Map<Long, EpisodeProgressResponseDto> result = new HashMap<>();
        collectInto(result, ids, redisTemplate.opsForHash().multiGet(FLUSHING_KEY, fields));
        collectInto(result, ids, redisTemplate.opsForHash().multiGet(BUFFER_KEY, fields)); // 최신값이 이기도록 나중에 덮는다
        return result;
    }

    /**
     * 버퍼에서 제거(진행률 삭제 시 — 남아 있으면 다음 flush 가 되살린다)
     */
    public void evict(Long userId, Collection<Long> episodeIds) {
        Object[] fields = episodeIds.stream().map(id -> (Object) field(userId, id)).toArray();
        if (fields.length == 0) return;
        redisTemplate.opsForHash().delete(BUFFER_KEY, fields);
        redisTemplate.opsForHash().delete(FLUSHING_KEY, fields);
    }

    /**
     * 버퍼 → DB 배치 반영
     * - 기본 10 초 주기(ott.progress.flush-interval-ms 로 조정)
     * - 인스턴스가 여러 개면 같은 스냅샷을 동시에 반영하려 하므로 ShedLock 으로 한 번만 돌게 한다.
     */
    @Scheduled(fixedDelayString = "${ott.progress.flush-interval-ms:10000}")
    @SchedulerLock(name = "ProgressBufferService_flush", lockAtMostFor = "PT1M", lockAtLeastFor = "PT1S")
    public void flush() {
        Map<Object, Object> snapshot = drain();
        if (snapshot.isEmpty()) return;

        List<EpisodeProgressFlushDto> rows = new ArrayList<>(snapshot.size());
        for (Map.Entry<Object, Object> e : snapshot.entrySet()) {
            String[] key = e.getKey().toString().split(":");
            EpisodeProgressResponseDto value = parse(e.getValue().toString());
            rows.add(EpisodeProgressFlushDto.builder()
                    .userId(Long.valueOf(key[0]))
                    .episodeId(Long.valueOf(key[1]))
                    .positionSec(value.getPositionSec())
                    .durationSec(value.getDurationSec())
                    .updatedAt(value.getUpdatedAt())
                    .build());
        }

        for (int i = 0; i < rows.size(); i += CHUNK_SIZE) {
            upsertChunk(rows.subList(i, Math.min(i + CHUNK_SIZE, rows.size())));
        }
        redisTemplate.delete(FLUSHING_KEY); // 반영이 끝난 스냅샷만 지운다
        log.debug("진행률 버퍼 반영 완료 - {}건", rows.size());
    }

    /**
     * 버퍼를 스냅샷으로 들어낸다.
     * - 앞선 flush 가 실패해 남은 스냅샷이 있으면 그것을 먼저 처리한다.
     * - rename 은 원자적이라 "읽고 지우는" 방식과 달리 그 사이에 들어온 쓰기를 잃지 않는다.
     */
    private Map<Object, Object> drain() {
        if (Boolean.FALSE.equals(redisTemplate.hasKey(FLUSHING_KEY))) {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(BUFFER_KEY))) return Map.of();
            redisTemplate.rename(BUFFER_KEY, FLUSHING_KEY);
        }
        return redisTemplate.<Object, Object>opsForHash().entries(FLUSHING_KEY);
    }

    /**
     * 청크 단위 upsert. 한 행이 FK 위반(에피소드/사용자 삭제 등)이면 청크 전체가 실패하므로
     * 그때만 행 단위로 다시 시도해 문제 행만 버린다.
     */
    private void upsertChunk(List<EpisodeProgressFlushDto> chunk) {
        try {
            progressQueryMapper.upsertProgressBatch(chunk);
        } catch (Exception ex) {
            log.warn("진행률 배치 반영 실패({}건) - 행 단위로 재시도한다", chunk.size(), ex);
            for (EpisodeProgressFlushDto row : chunk) {
                try {
                    progressQueryMapper.upsertProgressBatch(List.of(row));
                } catch (Exception rowEx) {
                    log.error("진행률 반영 실패 - userId: {}, episodeId: {} (버림)",
                            row.getUserId(), row.getEpisodeId(), rowEx);
                }
            }
        }
    }

    private void collectInto(Map<Long, EpisodeProgressResponseDto> target, List<Long> ids, List<Object> values) {
        if (values == null) return;
        for (int i = 0; i < values.size(); i++) {
            Object v = values.get(i);
            if (v != null) target.put(ids.get(i), parse(v.toString()));
        }
    }

    private String field(Long userId, Long episodeId) {
        return userId + ":" + episodeId;
    }

    /**
     * 버퍼 값 형식: "위치:길이:기록시각(epoch millis)"
     * JSON 대신 구분자를 쓴다 — 요청마다 직렬화가 도는 자리라 비용을 최소화한다.
     */
    private EpisodeProgressResponseDto parse(String raw) {
        String[] parts = raw.split(":");
        return EpisodeProgressResponseDto.builder()
                .positionSec(Integer.valueOf(parts[0]))
                .durationSec(Integer.valueOf(parts[1]))
                .updatedAt(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(Long.parseLong(parts[2])), ZoneId.systemDefault()))
                .build();
    }
}
