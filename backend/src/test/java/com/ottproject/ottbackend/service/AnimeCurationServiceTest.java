package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.admin.AdminAnimeListItemDto;
import com.ottproject.ottbackend.dto.admin.AnimeCurationUpdateRequest;
import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.curation.AnimeCurationQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 단건 큐레이션 수정 규칙 검증
 *
 * 여기서 고정하는 규칙
 * - 부분 수정: null 인 필드는 건드리지 않는다.
 * - curated 는 '보강이 덮어쓰는 콘텐츠 필드'가 실제로 바뀌었을 때만 켠다.
 *   배지만 토글했다고 켜면 그 작품의 제목 보강이 영구히 막힌다(과잉 차단).
 * - save() 를 부르지 않는다 — 영속 엔티티라 더티 체킹으로 반영된다.
 */
@ExtendWith(MockitoExtension.class)
class AnimeCurationServiceTest {

    @Mock private AnimeCurationQueryRepository curationQueryRepository;
    @Mock private AnimeRepository animeRepository;

    @InjectMocks private AnimeCurationService animeCurationService;

    private static final Long ANIME_ID = 1L;

    private Anime anime;

    @BeforeEach
    void setUp() {
        anime = new Anime();
        ReflectionTestUtils.setField(anime, "id", ANIME_ID);
        anime.setTitle("기존 제목");
        anime.setPosterUrl("http://old/poster.jpg");
        anime.setStatus(AnimeStatus.ONGOING);
        anime.setIsActive(true);
        anime.setIsExclusive(false);
        anime.setIsPopular(false);
        anime.setIsNew(false);
        anime.setCurated(false);
    }

    private AnimeCurationUpdateRequest emptyRequest() {
        return new AnimeCurationUpdateRequest();
    }

    private void givenAnimeExists() {
        given(animeRepository.findById(ANIME_ID)).willReturn(Optional.of(anime));
    }

    @Nested
    @DisplayName("부분 수정")
    class PartialUpdate {

        @Test
        @DisplayName("전달한 필드만 바꾸고 나머지는 그대로 둔다")
        void changesOnlyProvidedFields() {
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setIsPopular(true);

            animeCurationService.update(ANIME_ID, request);

            assertThat(anime.getIsPopular()).isTrue();
            assertThat(anime.getTitle()).isEqualTo("기존 제목");        // 안 건드림
            assertThat(anime.getPosterUrl()).isEqualTo("http://old/poster.jpg");
            assertThat(anime.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("배지를 끌 수도 있다 - false 는 '변경 없음'이 아니다")
        void canTurnBadgeOff() {
            anime.setIsExclusive(true);
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setIsExclusive(false);

            animeCurationService.update(ANIME_ID, request);

            assertThat(anime.getIsExclusive()).isFalse();
        }

        @Test
        @DisplayName("작품을 비활성화해 사용자 목록에서 내릴 수 있다 - 이전에는 방법이 없던 동작")
        void canDeactivateAnime() {
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setIsActive(false);

            animeCurationService.update(ANIME_ID, request);

            assertThat(anime.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("엔티티를 save 하지 않는다 - 영속 상태라 더티 체킹으로 반영된다")
        void doesNotCallSave() {
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setIsPopular(true);

            animeCurationService.update(ANIME_ID, request);

            verify(animeRepository, never()).save(any(Anime.class));
        }

        @Test
        @DisplayName("없는 작품을 수정하면 404")
        void rejectsUnknownAnime() {
            given(animeRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> animeCurationService.update(999L, emptyRequest()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("404");
        }
    }

    @Nested
    @DisplayName("curated 표시 규칙")
    class CuratedFlagRule {

        @Test
        @DisplayName("제목을 고치면 curated 가 켜진다 - 이후 자동 보강이 건너뛴다")
        void marksCuratedWhenTitleChanges() {
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setTitle("고친 제목");

            animeCurationService.update(ANIME_ID, request);

            assertThat(anime.getTitle()).isEqualTo("고친 제목");
            assertThat(anime.getCurated()).isTrue();
        }

        @Test
        @DisplayName("포스터를 고쳐도 curated 가 켜진다")
        void marksCuratedWhenPosterChanges() {
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setPosterUrl("http://new/poster.jpg");

            animeCurationService.update(ANIME_ID, request);

            assertThat(anime.getCurated()).isTrue();
        }

        @Test
        @DisplayName("배지만 토글하면 curated 를 켜지 않는다 - 제목 보강까지 막으면 과잉이다")
        void doesNotMarkCuratedForBadgeOnlyChange() {
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setIsPopular(true);
            request.setIsExclusive(true);
            request.setIsActive(false);

            animeCurationService.update(ANIME_ID, request);

            assertThat(anime.getCurated()).isFalse();
        }

        @Test
        @DisplayName("같은 값을 다시 보내면 curated 를 켜지 않는다 - 실제로 바뀐 게 없다")
        void doesNotMarkCuratedWhenValueIsUnchanged() {
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setTitle("기존 제목"); // 현재 값과 동일

            animeCurationService.update(ANIME_ID, request);

            assertThat(anime.getCurated()).isFalse();
        }

        @Test
        @DisplayName("이미 curated 인 작품은 배지만 바꿔도 curated 를 유지한다")
        void keepsCuratedOnceSet() {
            anime.setCurated(true);
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setIsPopular(true);

            animeCurationService.update(ANIME_ID, request);

            assertThat(anime.getCurated()).isTrue();
        }

        @Test
        @DisplayName("비어 있던 제목을 채워도 curated 가 켜진다 - 보강이 채울 자리를 사람이 먼저 채운 것")
        void marksCuratedWhenFillingEmptyTitle() {
            anime.setTitle(null);
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setTitle("사람이 넣은 제목");

            animeCurationService.update(ANIME_ID, request);

            assertThat(anime.getCurated()).isTrue();
        }
    }

    @Nested
    @DisplayName("단건 조회")
    class GetOne {

        @Test
        @DisplayName("현재 값을 그대로 돌려준다")
        void returnsCurrentValues() {
            givenAnimeExists();

            AdminAnimeListItemDto dto = animeCurationService.get(ANIME_ID);

            assertThat(dto.getId()).isEqualTo(ANIME_ID);
            assertThat(dto.getTitle()).isEqualTo("기존 제목");
        }

        @Test
        @DisplayName("없는 작품은 404")
        void rejectsUnknownAnime() {
            given(animeRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> animeCurationService.get(999L))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }
}
