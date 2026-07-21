package com.ottproject.ottbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ottproject.ottbackend.dto.AnimeDetailDto;
import com.ottproject.ottbackend.dto.GenreSimpleDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * AnimeCacheService 단위 테스트
 *
 * 여기서 고정하는 규칙
 * - cache-aside: 미스면 DB 를 타고 TTL 과 함께 채운다, 히트면 DB 를 타지 않는다.
 * - 널(작품 없음) 상세는 캐시에 넣지 않는다.
 * - LocalDate/LocalDateTime 을 담은 DTO 도 주입 ObjectMapper 로 무사히 직렬화된다(RedisConfig 함정 회피).
 * - 무효화는 활성 트랜잭션에서 afterCommit 에 예약되고, 트랜잭션 밖이면 즉시 삭제된다.
 */
@ExtendWith(MockitoExtension.class)
class AnimeCacheServiceTest {

    private static final String GENRES_KEY = "ott:anime:genres:v1";
    private static final String POPULAR_KEY = "ott:anime:popular:v1";

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private AnimeQueryService animeQueryService;

    // 실제 Boot 구성과 동일하게 JavaTime 을 등록한 ObjectMapper — 직렬화 함정을 진짜로 검증하기 위함
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private AnimeCacheService service;

    @BeforeEach
    void setUp() {
        service = new AnimeCacheService(stringRedisTemplate, objectMapper, animeQueryService);
    }

    private GenreSimpleDto genre() {
        return GenreSimpleDto.builder().id(1L).name("액션").color("#f00").build();
    }

    @Nested
    @DisplayName("장르 cache-aside")
    class Genres {

        @Test
        @DisplayName("미스면 DB 를 타고 TTL 6시간으로 채운다")
        void missLoadsFromDbAndSets() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get(GENRES_KEY)).willReturn(null);
            given(animeQueryService.getAllGenres()).willReturn(List.of(genre()));

            List<GenreSimpleDto> result = service.getGenres();

            assertThat(result).hasSize(1);
            verify(animeQueryService).getAllGenres();
            verify(valueOps).set(eq(GENRES_KEY), anyString(), eq(Duration.ofHours(6)));
        }

        @Test
        @DisplayName("히트면 DB 를 타지 않고 캐시 값을 역직렬화해 돌려준다")
        void hitSkipsDb() throws Exception {
            String json = objectMapper.writeValueAsString(List.of(genre()));
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get(GENRES_KEY)).willReturn(json);

            List<GenreSimpleDto> result = service.getGenres();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("액션");
            verify(animeQueryService, never()).getAllGenres();
            verify(valueOps, never()).set(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("상세 공용부 cache-aside")
    class DetailPublic {

        private AnimeDetailDto detailWithDates() {
            return AnimeDetailDto.builder()
                    .aniId(5L)
                    .title("제목")
                    .releaseDate(LocalDate.of(2026, 1, 1)) // 직렬화 함정 대상
                    .createdAt(LocalDateTime.of(2026, 1, 1, 12, 0)) // 직렬화 함정 대상
                    .build();
        }

        @Test
        @DisplayName("널(작품 없음)은 캐시에 넣지 않고 null 을 돌려준다")
        void nullIsNotCached() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get("ott:anime:detail:v1:5")).willReturn(null);
            given(animeQueryService.detail(5L)).willReturn(null);

            AnimeDetailDto result = service.getDetailPublic(5L);

            assertThat(result).isNull();
            verify(valueOps, never()).set(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("LocalDate/LocalDateTime 을 담은 상세도 폴백 없이 한 번의 DB 조회로 캐시에 채운다")
        void serializesJavaTimeAndCaches() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get("ott:anime:detail:v1:5")).willReturn(null);
            given(animeQueryService.detail(5L)).willReturn(detailWithDates());

            AnimeDetailDto result = service.getDetailPublic(5L);

            assertThat(result).isNotNull();
            assertThat(result.getAniId()).isEqualTo(5L);
            // 직렬화가 터졌다면 catch 폴백이 detail 을 한 번 더 불렀을 것 — times(1) 로 함정 회피를 고정한다
            verify(animeQueryService, times(1)).detail(5L);
            verify(valueOps).set(eq("ott:anime:detail:v1:5"), anyString(), eq(Duration.ofMinutes(30)));
        }
    }

    @Nested
    @DisplayName("캐시 장애 폴백 (실패 케이스)")
    class Fallback {

        @Test
        @DisplayName("장르: Redis 조회가 터져도 예외를 던지지 않고 DB 값을 돌려준다")
        void genresFallsBackToDbOnRedisFailure() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get(GENRES_KEY)).willThrow(new RuntimeException("redis down"));
            given(animeQueryService.getAllGenres()).willReturn(List.of(genre()));

            List<GenreSimpleDto> result = service.getGenres();

            assertThat(result).hasSize(1); // 요청이 깨지지 않는다
            verify(animeQueryService).getAllGenres(); // DB 폴백을 탄다
        }

        @Test
        @DisplayName("상세: Redis 조회가 터져도 예외를 던지지 않고 DB 값을 돌려준다")
        void detailFallsBackToDbOnRedisFailure() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get("ott:anime:detail:v1:5")).willThrow(new RuntimeException("redis down"));
            given(animeQueryService.detail(5L)).willReturn(AnimeDetailDto.builder().aniId(5L).build());

            AnimeDetailDto result = service.getDetailPublic(5L);

            assertThat(result).isNotNull();
            assertThat(result.getAniId()).isEqualTo(5L);
            verify(animeQueryService).detail(5L);
        }

        @Test
        @DisplayName("무효화: afterCommit 삭제가 터져도 예외가 밖으로 새지 않는다")
        void evictSwallowsDeleteFailure() {
            given(stringRedisTemplate.delete(anyString())).willThrow(new RuntimeException("redis down"));

            // 트랜잭션 밖 즉시 삭제 경로 — 예외를 삼켜야 한다
            service.evictPopular();

            verify(stringRedisTemplate).delete(POPULAR_KEY);
        }
    }

    @Nested
    @DisplayName("무효화 타이밍")
    class Eviction {

        @Test
        @DisplayName("활성 트랜잭션이면 커밋 전엔 삭제하지 않고 afterCommit 에 예약한다")
        void registersAfterCommitWhenTransactionActive() {
            TransactionSynchronizationManager.initSynchronization();
            try {
                service.evictDetail(7L);

                // 아직 커밋 전 — 삭제되면 안 된다
                verify(stringRedisTemplate, never()).delete(anyString());

                // 커밋 시점 시뮬레이션
                for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
                    s.afterCommit();
                }

                verify(stringRedisTemplate).delete("ott:anime:detail:v1:7");
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        @DisplayName("트랜잭션 밖이면 즉시 삭제한다")
        void deletesImmediatelyWhenNoTransaction() {
            service.evictPopular();

            verify(stringRedisTemplate).delete(POPULAR_KEY);
        }
    }
}
