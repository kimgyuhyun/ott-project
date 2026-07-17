package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.repository.AnimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AnimeEnhancementService 의 보완 대상 선별/재확인 규칙 검증
 *
 * 회귀 배경
 * - enhanceAllAnime 은 @Async 라 트랜잭션이 없다. 예전에는 여기서 엔티티를 통째로 조회해 준영속 상태로
 *   들고 다니다가 항목마다 save() 했는데, 준영속 save() 는 merge 라 DB 의 현재 값 위에 배치 시작 시점의
 *   스냅샷 '전 필드'를 덮어썼다. 항목마다 1초를 쉬므로 배치는 몇 시간 돌고, 그동안 운영자가 한 수정은
 *   조용히 되돌아갔다.
 * - 이제 ID 만 들고 다니며 항목마다 트랜잭션 안에서 다시 조회한다. 이 테스트가 그 계약을 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // 일부 케이스는 TMDB 스텁까지 가지 않는다
class AnimeEnhancementServiceTest {

    @Mock private AnimeRepository animeRepository;
    @Mock private TmdbApiService tmdbApiService;
    @Mock private ObjectProvider<AnimeEnhancementService> selfProvider;

    private AnimeEnhancementService animeEnhancementService;

    @BeforeEach
    void setUp() {
        animeEnhancementService = new AnimeEnhancementService(animeRepository, tmdbApiService, selfProvider);
    }

    /**
     * 보완 대상 형태의 애니메(한국어 제목 없음).
     * Anime.createAnime 은 인자가 32개라 테스트 픽스처로 쓰기 어렵고, 여기서는 몇 필드만 필요하다.
     */
    private Anime animeWithoutKoreanTitle(Long id) {
        Anime anime = new Anime();
        ReflectionTestUtils.setField(anime, "id", id);
        anime.setTitleEn("Some Title");
        anime.setStatus(AnimeStatus.COMPLETED);
        anime.setCurated(Boolean.FALSE);
        return anime;
    }

    @Test
    @DisplayName("운영자가 큐레이션한 작품은 보완하지 않는다 - 외부 API 가 사람의 판단을 덮으면 안 된다")
    void skipsCuratedAnime() {
        Anime curated = animeWithoutKoreanTitle(1L);
        curated.setCurated(Boolean.TRUE);
        given(animeRepository.findById(1L)).willReturn(Optional.of(curated));

        boolean enhanced = animeEnhancementService.enhanceAnimeById(1L);

        assertThat(enhanced).isFalse();
        verify(tmdbApiService, never()).searchAnime(anyString()); // TMDB 를 부르지도 않는다
    }

    @Test
    @DisplayName("큐레이션되지 않은 작품은 TMDB 를 조회해 보완한다")
    void enhancesUncuratedAnime() {
        Anime anime = animeWithoutKoreanTitle(1L);
        given(animeRepository.findById(1L)).willReturn(Optional.of(anime));
        given(tmdbApiService.searchAnime("Some Title")).willReturn(koreanTmdbData("한국어 제목"));

        boolean enhanced = animeEnhancementService.enhanceAnimeById(1L);

        assertThat(enhanced).isTrue();
        assertThat(anime.getTitle()).isEqualTo("한국어 제목");
    }

    @Test
    @DisplayName("보완은 엔티티를 save 하지 않는다 - 영속 상태라 더티 체킹으로 반영된다")
    void doesNotCallSaveOnManagedEntity() {
        Anime anime = animeWithoutKoreanTitle(1L);
        given(animeRepository.findById(1L)).willReturn(Optional.of(anime));
        given(tmdbApiService.searchAnime("Some Title")).willReturn(koreanTmdbData("한국어 제목"));

        animeEnhancementService.enhanceAnimeById(1L);

        // 준영속 엔티티에 save() 를 하면 merge 가 되어 낡은 스냅샷 전체를 덮어쓴다. 그 경로가 없어야 한다.
        verify(animeRepository, never()).save(any(Anime.class));
    }

    @Test
    @DisplayName("존재하지 않는 ID 는 조용히 실패한다")
    void returnsFalseForUnknownId() {
        given(animeRepository.findById(999L)).willReturn(Optional.empty());

        assertThat(animeEnhancementService.enhanceAnimeById(999L)).isFalse();
    }

    @Test
    @DisplayName("배치는 대상마다 ID 로 다시 조회한다 - 몇 시간 묵은 스냅샷을 쓰지 않는다")
    void batchReEntersPerItemThroughProxy() {
        Anime target = animeWithoutKoreanTitle(1L);
        given(animeRepository.findByTitleIsNullAndCuratedIsFalse()).willReturn(List.of(target));
        // 프록시 경유 호출이라야 항목마다 @Transactional 이 새로 걸린다.
        AnimeEnhancementService proxy = org.mockito.Mockito.mock(AnimeEnhancementService.class);
        given(selfProvider.getObject()).willReturn(proxy);
        given(proxy.enhanceAnimeById(1L)).willReturn(true);

        animeEnhancementService.enhanceAllAnime();

        // 엔티티를 직접 넘기지 않고 ID 로 재진입했는지가 회귀 방지의 핵심이다
        verify(proxy).enhanceAnimeById(1L);
    }

    @Test
    @DisplayName("배치 대상 조회에서 큐레이션된 작품이 애초에 빠진다")
    void batchQueryExcludesCuratedAnime() {
        given(animeRepository.findByTitleIsNullAndCuratedIsFalse()).willReturn(List.of());

        animeEnhancementService.enhanceAllAnime();

        verify(animeRepository).findByTitleIsNullAndCuratedIsFalse();
        verify(animeRepository, never()).findByTitle(anyString());
    }

    private TmdbApiService.TmdbAnimeData koreanTmdbData(String title) {
        TmdbApiService.TmdbAnimeData data = new TmdbApiService.TmdbAnimeData();
        data.setTitle(title);
        data.setHasKoreanData(true);
        return data;
    }
}
