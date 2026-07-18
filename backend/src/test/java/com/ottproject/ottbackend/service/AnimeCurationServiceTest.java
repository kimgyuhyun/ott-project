package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.admin.AdminAnimeDetailDto;
import com.ottproject.ottbackend.dto.admin.AnimeBulkCurationRequest;
import com.ottproject.ottbackend.dto.admin.AnimeCurationSearchCondition;
import com.ottproject.ottbackend.dto.admin.AnimeCurationUpdateRequest;
import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.curation.AnimeCurationQueryRepository;
import jakarta.persistence.EntityManager;
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
    @Mock private EntityManager entityManager;

    @InjectMocks private AnimeCurationService animeCurationService;

    private static final Long ANIME_ID = 1L;

    private Anime anime;

    @BeforeEach
    void setUp() {
        // EntityManager 는 @PersistenceContext 필드 주입이라 @InjectMocks 가 채우지 못한다.
        ReflectionTestUtils.setField(animeCurationService, "entityManager", entityManager);

        anime = new Anime();
        ReflectionTestUtils.setField(anime, "id", ANIME_ID);
        anime.setTitle("기존 제목");
        anime.setPosterUrl("http://old/poster.jpg");
        anime.setStatus(AnimeStatus.ONGOING);
        anime.setIsActive(true);
        anime.setIsExclusive(false);
        anime.setIsPopular(false);
        anime.setIsNew(false);
        anime.setIsCompleted(false);
        anime.setIsSubtitle(true);
        anime.setIsDub(false);
        anime.setIsSimulcast(false);
        anime.setCurated(false);
    }

    private AnimeCurationUpdateRequest emptyRequest() {
        return new AnimeCurationUpdateRequest();
    }

    private void givenAnimeExists() {
        given(animeRepository.findById(ANIME_ID)).willReturn(Optional.of(anime));
    }

    /** 조회 경로는 락 없는 findByIdWithoutLock 을 탄다(GetOne 주석 참고). */
    private void givenAnimeExistsForRead() {
        given(animeRepository.findByIdWithoutLock(ANIME_ID)).willReturn(Optional.of(anime));
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

        /**
         * curated 는 "보강이 이 작품을 건너뛴다"는 뜻이다. 그러므로 보강이 덮어쓰는 필드 전부가
         * 판정 대상이어야 한다 — 줄거리/배경이미지를 고쳤는데 켜지지 않으면 보강이 도로 덮어쓴다.
         * (AnimeEnhancementService 가 쓰는 필드: title, synopsis, fullSynopsis, backdropUrl, posterUrl)
         */
        @Test
        @DisplayName("줄거리를 고쳐도 curated 가 켜진다 - 보강이 덮어쓰는 필드다")
        void marksCuratedWhenSynopsisChanges() {
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setSynopsis("사람이 고친 줄거리");

            animeCurationService.update(ANIME_ID, request);

            assertThat(anime.getSynopsis()).isEqualTo("사람이 고친 줄거리");
            assertThat(anime.getCurated()).isTrue();
        }

        @Test
        @DisplayName("전체 줄거리를 고쳐도 curated 가 켜진다")
        void marksCuratedWhenFullSynopsisChanges() {
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setFullSynopsis("사람이 고친 전체 줄거리");

            animeCurationService.update(ANIME_ID, request);

            assertThat(anime.getCurated()).isTrue();
        }

        @Test
        @DisplayName("배경 이미지를 고쳐도 curated 가 켜진다")
        void marksCuratedWhenBackdropChanges() {
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setBackdropUrl("http://new/backdrop.jpg");

            animeCurationService.update(ANIME_ID, request);

            assertThat(anime.getCurated()).isTrue();
        }

        @Test
        @DisplayName("자막/더빙 배지만 바꾸면 curated 를 켜지 않는다 - 보강이 안 쓰는 필드다")
        void doesNotMarkCuratedForNewBadgesOnly() {
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setIsDub(true);
            request.setIsSubtitle(false);
            request.setIsSimulcast(true);
            request.setIsCompleted(true);

            animeCurationService.update(ANIME_ID, request);

            assertThat(anime.getCurated()).isFalse();
        }
    }

    @Nested
    @DisplayName("휴리스틱으로 찍힌 배지 바로잡기")
    class HeuristicBadges {

        /**
         * 수집 시 isDub 은 평점으로 추측되고(determineIsDub), isSubtitle 은 항상 true 로 박힌다.
         * 이 값들이 사용자 화면의 필터로 그대로 노출되므로 사람이 고칠 수 있어야 한다.
         */
        @Test
        @DisplayName("더빙 여부를 바로잡을 수 있다 - 수집 시 평점으로 추측된 값이다")
        void canCorrectDubFlag() {
            anime.setIsDub(true); // 평점 기반 추측 결과
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setIsDub(false);

            animeCurationService.update(ANIME_ID, request);

            assertThat(anime.getIsDub()).isFalse();
        }

        @Test
        @DisplayName("자막 여부를 끌 수 있다 - 수집 시 항상 true 로 박힌다")
        void canTurnOffSubtitleFlag() {
            anime.setIsSubtitle(true);
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setIsSubtitle(false);

            animeCurationService.update(ANIME_ID, request);

            assertThat(anime.getIsSubtitle()).isFalse();
        }

        @Test
        @DisplayName("동시방영/완결 여부도 바로잡을 수 있다")
        void canCorrectSimulcastAndCompleted() {
            givenAnimeExists();
            AnimeCurationUpdateRequest request = emptyRequest();
            request.setIsSimulcast(true);
            request.setIsCompleted(true);

            animeCurationService.update(ANIME_ID, request);

            assertThat(anime.getIsSimulcast()).isTrue();
            assertThat(anime.getIsCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("벌크 수정 안전장치")
    class BulkGuards {

        private AnimeCurationSearchCondition yearCondition() {
            AnimeCurationSearchCondition condition = new AnimeCurationSearchCondition();
            condition.setYear(2026);
            return condition;
        }

        private AnimeBulkCurationRequest bulkRequest(AnimeCurationSearchCondition condition, long expectedCount) {
            AnimeBulkCurationRequest request = new AnimeBulkCurationRequest();
            request.setCondition(condition);
            request.setIsActive(false);
            request.setExpectedCount(expectedCount);
            return request;
        }

        @Test
        @DisplayName("조건이 비면 거부한다 - 빈 조건은 카탈로그 전체를 뒤집는다")
        void rejectsEmptyCondition() {
            AnimeBulkCurationRequest request = bulkRequest(new AnimeCurationSearchCondition(), 0);

            assertThatThrownBy(() -> animeCurationService.applyBulkCuration(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("400");

            verify(curationQueryRepository, never()).applyBulkCuration(any(), any());
        }

        @Test
        @DisplayName("조건 객체 자체가 없어도 거부한다")
        void rejectsNullCondition() {
            AnimeBulkCurationRequest request = bulkRequest(null, 0);

            assertThatThrownBy(() -> animeCurationService.applyBulkCuration(request))
                    .isInstanceOf(ResponseStatusException.class);

            verify(curationQueryRepository, never()).applyBulkCuration(any(), any());
        }

        @Test
        @DisplayName("공백뿐인 제목 키워드는 조건으로 치지 않아 거부된다")
        void rejectsBlankKeywordAsOnlyCondition() {
            AnimeCurationSearchCondition condition = new AnimeCurationSearchCondition();
            condition.setTitleKeyword("   ");
            AnimeBulkCurationRequest request = bulkRequest(condition, 0);

            assertThatThrownBy(() -> animeCurationService.applyBulkCuration(request))
                    .isInstanceOf(ResponseStatusException.class);

            verify(curationQueryRepository, never()).applyBulkCuration(any(), any());
        }

        @Test
        @DisplayName("바꿀 값이 없으면 거부한다")
        void rejectsRequestWithNoChanges() {
            AnimeBulkCurationRequest request = new AnimeBulkCurationRequest();
            request.setCondition(yearCondition());
            request.setExpectedCount(3);

            assertThatThrownBy(() -> animeCurationService.applyBulkCuration(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("400");

            verify(curationQueryRepository, never()).applyBulkCuration(any(), any());
        }

        @Test
        @DisplayName("미리보기 건수와 실제가 다르면 409 로 중단한다 - 승인한 것보다 많이 바뀌면 안 된다")
        void rejectsCountMismatch() {
            AnimeCurationSearchCondition condition = yearCondition();
            given(curationQueryRepository.countByCondition(condition)).willReturn(50L); // 그새 늘어남
            AnimeBulkCurationRequest request = bulkRequest(condition, 3);

            assertThatThrownBy(() -> animeCurationService.applyBulkCuration(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("409");

            verify(curationQueryRepository, never()).applyBulkCuration(any(), any());
        }

        @Test
        @DisplayName("건수가 일치하면 적용하고 영향 건수를 돌려준다")
        void appliesWhenCountMatches() {
            AnimeCurationSearchCondition condition = yearCondition();
            given(curationQueryRepository.countByCondition(condition)).willReturn(3L);
            AnimeBulkCurationRequest request = bulkRequest(condition, 3);
            given(curationQueryRepository.applyBulkCuration(condition, request)).willReturn(3L);

            assertThat(animeCurationService.applyBulkCuration(request)).isEqualTo(3L);
        }

        @Test
        @DisplayName("벌크 전에 flush 하고 후에 clear 한다 - 더티 체킹이 벌크를 되돌리거나 1차 캐시가 낡으면 안 된다")
        void flushesBeforeAndClearsAfter() {
            AnimeCurationSearchCondition condition = yearCondition();
            given(curationQueryRepository.countByCondition(condition)).willReturn(3L);
            AnimeBulkCurationRequest request = bulkRequest(condition, 3);
            given(curationQueryRepository.applyBulkCuration(condition, request)).willReturn(3L);

            animeCurationService.applyBulkCuration(request);

            org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(entityManager, curationQueryRepository);
            inOrder.verify(entityManager).flush();
            inOrder.verify(curationQueryRepository).applyBulkCuration(condition, request);
            inOrder.verify(entityManager).clear();
        }

        @Test
        @DisplayName("미리보기도 조건 없이는 거부한다")
        void previewRejectsEmptyCondition() {
            assertThatThrownBy(() -> animeCurationService.previewBulkCuration(new AnimeCurationSearchCondition()))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        @DisplayName("미리보기는 건수와 표본을 돌려주고 아무것도 바꾸지 않는다")
        void previewReturnsCountAndSampleWithoutChanging() {
            AnimeCurationSearchCondition condition = yearCondition();
            given(curationQueryRepository.countByCondition(condition)).willReturn(42L);
            given(curationQueryRepository.search(condition, 0, 10)).willReturn(java.util.List.of(anime));

            var preview = animeCurationService.previewBulkCuration(condition);

            assertThat(preview.getAffectedCount()).isEqualTo(42L);
            assertThat(preview.getSample()).hasSize(1);
            verify(curationQueryRepository, never()).applyBulkCuration(any(), any());
        }
    }

    @Nested
    @DisplayName("단건 조회")
    class GetOne {

        /**
         * 이 트랜잭션은 readOnly 다. 락 걸린 findById 를 쓰면 PostgreSQL 이 SELECT ... FOR UPDATE 를 거부해
         * ("cannot execute SELECT FOR UPDATE in a read-only transaction") 수정 폼을 여는 것만으로 500 이 난다.
         * 실제로 그렇게 죽었다.
         *
         * 한계: H2 는 read-only 트랜잭션에서 FOR UPDATE 를 막지 않아 슬라이스 테스트로 재현되지 않는다.
         * 그래서 "어느 조회 메서드를 쓰는가"를 아래 usesLockFreeLookup 으로 고정한다.
         */
        @Test
        @DisplayName("락 없는 조회를 쓴다 - findById(FOR UPDATE)로 되돌리면 500 이 난다")
        void usesLockFreeLookup() {
            givenAnimeExistsForRead();

            animeCurationService.get(ANIME_ID);

            verify(animeRepository, never()).findById(ANIME_ID);
        }

        @Test
        @DisplayName("현재 값을 그대로 돌려준다")
        void returnsCurrentValues() {
            givenAnimeExistsForRead();

            AdminAnimeDetailDto dto = animeCurationService.get(ANIME_ID);

            assertThat(dto.getId()).isEqualTo(ANIME_ID);
            assertThat(dto.getTitle()).isEqualTo("기존 제목");
        }

        @Test
        @DisplayName("목록에 없는 줄거리까지 준다 - 수정 폼이 채워야 하는 값이다")
        void includesSynopsisWhichListOmits() {
            anime.setSynopsis("짧은 줄거리");
            anime.setFullSynopsis("전체 줄거리");
            givenAnimeExistsForRead();

            AdminAnimeDetailDto dto = animeCurationService.get(ANIME_ID);

            assertThat(dto.getSynopsis()).isEqualTo("짧은 줄거리");
            assertThat(dto.getFullSynopsis()).isEqualTo("전체 줄거리");
        }

        @Test
        @DisplayName("없는 작품은 404")
        void rejectsUnknownAnime() {
            given(animeRepository.findByIdWithoutLock(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> animeCurationService.get(999L))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

}
