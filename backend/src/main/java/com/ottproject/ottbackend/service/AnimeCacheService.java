package com.ottproject.ottbackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.dto.AnimeDetailDto;
import com.ottproject.ottbackend.dto.AnimeListDto;
import com.ottproject.ottbackend.dto.GenreSimpleDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;

/**
 * AnimeCacheService
 *
 * 큰 흐름
 * - 애니 조회(장르/인기/상세 공용부)에 손구현 cache-aside 를 얹는다.
 * - RecentSearchService 관례를 따른다: 콜론 네임스페이스 키 + v1 세그먼트 + TTL + try/catch 폴백 + 로깅.
 * - 직렬화는 StringRedisTemplate + 주입 ObjectMapper(JSON). RedisConfig 의 GenericJackson2 직렬화기는
 *   JavaTimeModule 이 없어 LocalDate/LocalDateTime 을 담은 DTO 에서 터진다 — 그래서 여기선 쓰지 않는다.
 *
 * 메서드 개요
 * - getGenres/getPopular/getDetailPublic: cache-aside 읽기(get→미스면 DB→set)
 * - evictGenres/evictPopular/evictDetail: 커밋 후 무효화(afterCommit 삭제)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnimeCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AnimeQueryService animeQueryService;

    private static final String GENRES_KEY = "ott:anime:genres:v1";
    private static final String POPULAR_KEY = "ott:anime:popular:v1";
    private static final String DETAIL_KEY_PREFIX = "ott:anime:detail:v1:";

    private static final Duration GENRES_TTL = Duration.ofHours(6);
    private static final Duration POPULAR_TTL = Duration.ofMinutes(5);
    private static final Duration DETAIL_TTL = Duration.ofMinutes(30);

    /**
     * 장르 목록 조회(cache-aside). 미스 시 DB 조회 후 캐시에 채운다.
     */
    public List<GenreSimpleDto> getGenres() {
        try {
            String json = stringRedisTemplate.opsForValue().get(GENRES_KEY);
            if (json != null) {
                log.info("[Cache][Anime] genres HIT key={}", GENRES_KEY);
                return objectMapper.readValue(json, new TypeReference<List<GenreSimpleDto>>() {});
            }

            log.info("[Cache][Anime] genres MISS key={}", GENRES_KEY);
            List<GenreSimpleDto> data = animeQueryService.getAllGenres();
            stringRedisTemplate.opsForValue().set(GENRES_KEY, objectMapper.writeValueAsString(data), GENRES_TTL);
            return data;

        } catch (Exception e) {
            log.error("[Cache][Anime] genres failed key={} error={} - DB 폴백", GENRES_KEY, e.getMessage(), e);
            return animeQueryService.getAllGenres();
        }
    }

    /**
     * 인기 목록 조회(cache-aside). 미스 시 고정 쿼리로 DB 조회 후 캐시에 채운다.
     *
     * list 호출 인자는 AnimeController.getPopular() 본문에서 그대로 옮겨온 것이다(rating 정렬, size=10).
     */
    public List<AnimeListDto> getPopular() {
        try {
            String json = stringRedisTemplate.opsForValue().get(POPULAR_KEY);
            if (json != null) {
                log.info("[Cache][Anime] popular HIT key={}", POPULAR_KEY);
                return objectMapper.readValue(json, new TypeReference<List<AnimeListDto>>() {});
            }

            log.info("[Cache][Anime] popular MISS key={}", POPULAR_KEY);
            List<AnimeListDto> data = animeQueryService.list(
                    null, // status
                    null, // genreIds
                    null, // minRating
                    null, // year
                    null, // quarter
                    null, // type
                    null, // isDub
                    null, // isSubtitle
                    null, // isExclusive
                    null, // isCompleted
                    null, // isNew
                    null, // isPopular
                    "rating", // sort
                    0, // page
                    10, // size
                    null // tagIds
            ).getItems();
            stringRedisTemplate.opsForValue().set(POPULAR_KEY, objectMapper.writeValueAsString(data), POPULAR_TTL);
            return data;

        } catch (Exception e) {
            log.error("[Cache][Anime] popular failed key={} error={} - DB 폴백", POPULAR_KEY, e.getMessage(), e);
            return animeQueryService.list(
                    null, null, null, null, null, null,
                    null, null, null, null, null, null,
                    "rating", 0, 10, null
            ).getItems();
        }
    }

    /**
     * 상세 공용부 조회(cache-aside). isFavorited 는 담지 않는다 — 요청마다 호출부에서 따로 합성한다.
     * 작품이 없으면(null) 캐시에 넣지 않고 null 을 반환한다.
     */
    public AnimeDetailDto getDetailPublic(Long aniId) {
        String key = detailKey(aniId);
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                log.info("[Cache][Anime] detail HIT key={}", key);
                return objectMapper.readValue(json, AnimeDetailDto.class);
            }

            log.info("[Cache][Anime] detail MISS key={}", key);
            AnimeDetailDto data = animeQueryService.detail((long) aniId); // 공용 오버로드(찜여부 미포함)
            if (data == null) {
                return null; // 작품 없음은 캐시하지 않는다
            }
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data), DETAIL_TTL);
            return data;

        } catch (Exception e) {
            log.error("[Cache][Anime] detail failed key={} error={} - DB 폴백", key, e.getMessage(), e);
            return animeQueryService.detail((long) aniId);
        }
    }

    /**
     * 상세 무효화(커밋 후). 관리자 쓰기 경로에서 호출한다.
     */
    public void evictDetail(Long aniId) {
        evictAfterCommit(detailKey(aniId));
    }

    /**
     * 인기 무효화(커밋 후).
     */
    public void evictPopular() {
        evictAfterCommit(POPULAR_KEY);
    }

    /**
     * 장르 무효화(커밋 후).
     */
    public void evictGenres() {
        evictAfterCommit(GENRES_KEY);
    }

    private String detailKey(Long aniId) {
        return DETAIL_KEY_PREFIX + aniId;
    }

    /**
     * 무효화 타이밍(핵심): 반드시 커밋 후에 삭제한다.
     * 메서드 본문에서 즉시 delete 하면, 삭제 직후 다른 요청이 아직 커밋 안 된 옛 값을 다시 캐시에 채우는 레이스가 난다.
     * 활성 트랜잭션이 있으면 afterCommit 에 삭제를 예약하고, 없으면(트랜잭션 밖 호출) 즉시 삭제한다.
     */
    private void evictAfterCommit(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        stringRedisTemplate.delete(key);
                        log.info("[Cache][Anime] evict(afterCommit) key={}", key);
                    } catch (Exception e) {
                        log.error("[Cache][Anime] evict(afterCommit) failed key={} error={}", key, e.getMessage(), e);
                    }
                }
            });
        } else {
            try {
                stringRedisTemplate.delete(key);
                log.info("[Cache][Anime] evict(immediate) key={}", key);
            } catch (Exception e) {
                log.error("[Cache][Anime] evict(immediate) failed key={} error={}", key, e.getMessage(), e);
            }
        }
    }
}
