package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.exception.AdultContentException;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.CharacterRepository;
import com.ottproject.ottbackend.repository.DirectorRepository;
import com.ottproject.ottbackend.repository.VoiceActorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * SimpleAnimeDataCollectorService 일괄 수집 단위 테스트
 *
 * 지키려는 규칙
 * - 항목 하나가 실패해도 배치 전체가 죽으면 안 된다(실패는 집계만 하고 계속 진행).
 * - 각 항목은 프록시(selfProvider)를 통해 호출되어 항목별 트랜잭션을 갖는다.
 *
 * 회귀 배경(2026-07-16)
 * - collectPopularAnime 이 루프 전체를 하나의 트랜잭션으로 감싼 채 collectAnime 을 직접 호출(self-invocation)했다.
 * - 항목 하나가 실패하면 내부 @Transactional 빈이 공유 트랜잭션을 rollback-only 로 마킹했고,
 *   루프는 예외를 삼키고 끝까지 돌다가 마지막 커밋에서
 *   "Transaction silently rolled back because it has been marked as rollback-only" 로 배치 전체가 실패했다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // 생성자 주입 목이 많아 미사용 스텁 허용
class SimpleAnimeDataCollectorServiceTest {

    @Mock private SimpleJikanApiService jikanApiService;
    @Mock private SimpleJikanDataMapper dataMapper;
    @Mock private AnimeRepository animeRepository;
    @Mock private VoiceActorRepository voiceActorRepository;
    @Mock private CharacterRepository characterRepository;
    @Mock private DirectorRepository directorRepository;
    @Mock private AnimeBatchProcessor animeBatchProcessor;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private ObjectProvider<SimpleAnimeDataCollectorService> selfProvider;

    /** 프록시로 얻어지는 자기 자신(항목별 트랜잭션이 적용되는 경로) */
    @Mock private SimpleAnimeDataCollectorService self;

    @InjectMocks
    private SimpleAnimeDataCollectorService service;

    @BeforeEach
    void setUp() {
        given(selfProvider.getObject()).willReturn(self);
    }

    @Test
    @DisplayName("항목 하나가 예외로 실패해도 나머지는 계속 수집된다")
    void oneFailureDoesNotAbortTheBatch() {
        given(jikanApiService.getPopularAnimeIds(anyInt())).willReturn(List.of(1L, 2L, 3L));
        given(self.collectAnime(1L)).willReturn(true);
        given(self.collectAnime(2L)).willThrow(new RuntimeException("DB 오류")); // 중간 항목 실패
        given(self.collectAnime(3L)).willReturn(true);

        SimpleAnimeDataCollectorService.CollectionResult result = service.collectPopularAnime(3);

        // 실패 1건은 집계만 되고, 앞뒤 항목은 정상 수집되어야 한다
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getErrorCount()).isEqualTo(1);
        assertThat(result.getTotalProcessed()).isEqualTo(3);
    }

    @Test
    @DisplayName("19금 콘텐츠는 오류가 아니라 별도로 집계된다")
    void adultContentIsCountedSeparately() {
        given(jikanApiService.getPopularAnimeIds(anyInt())).willReturn(List.of(1L, 2L));
        given(self.collectAnime(1L)).willReturn(true);
        given(self.collectAnime(2L)).willThrow(new AdultContentException("19금"));

        SimpleAnimeDataCollectorService.CollectionResult result = service.collectPopularAnime(2);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getAdultContentCount()).isEqualTo(1);
        assertThat(result.getErrorCount()).isZero(); // 19금은 오류가 아님
    }

    @Test
    @DisplayName("각 항목은 프록시(selfProvider)를 통해 호출된다 - 항목별 트랜잭션 보장")
    void eachItemGoesThroughTheProxy() {
        given(jikanApiService.getPopularAnimeIds(anyInt())).willReturn(List.of(1L, 2L));
        given(self.collectAnime(1L)).willReturn(true);
        given(self.collectAnime(2L)).willReturn(true);

        service.collectPopularAnime(2);

        // 직접 호출(self-invocation)로 되돌아가면 프록시를 안 타 항목별 트랜잭션이 사라진다 → 이 검증이 깨진다
        verify(selfProvider, org.mockito.Mockito.times(2)).getObject();
        verify(self).collectAnime(1L);
        verify(self).collectAnime(2L);
    }

    @Test
    @DisplayName("수집할 목록이 비어 있으면 아무것도 처리하지 않는다")
    void emptyListProcessesNothing() {
        given(jikanApiService.getPopularAnimeIds(anyInt())).willReturn(List.of());

        SimpleAnimeDataCollectorService.CollectionResult result = service.collectPopularAnime(10);

        assertThat(result.getTotalProcessed()).isZero();
    }
}
