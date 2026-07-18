package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.admin.AdminEpisodeDetailDto;
import com.ottproject.ottbackend.dto.admin.EpisodeCreateRequest;
import com.ottproject.ottbackend.dto.admin.EpisodeUpdateRequest;
import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.entity.Episode;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.EpisodeRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AdminEpisodeService 단위 테스트
 *
 * 지키려는 규칙
 * - 에피소드가 저장되면 찜한 사용자 알림 트리거가 반드시 호출된다.
 *   이 호출이 이 API 를 만든 이유다 — 그 전까지 triggerEpisodeUpdateNotification 은 호출부가 없는 죽은 코드였다.
 * - 같은 작품에 같은 화수를 두 번 등록할 수 없다(중복 등록 시 알림도 두 번 나간다).
 */
@ExtendWith(MockitoExtension.class)
class AdminEpisodeServiceTest {

    @Mock private AnimeRepository animeRepository;
    @Mock private EpisodeRepository episodeRepository;
    @Mock private NotificationTriggerService notificationTriggerService;

    @InjectMocks
    private AdminEpisodeService service;

    private Anime anime;

    /**
     * Anime.createAnime 은 인자가 많아 픽스처로 쓰기 어렵다. 여기서는 id/title 만 필요하다.
     */
    @BeforeEach
    void setUp() {
        anime = new Anime();
        anime.setId(1L);
        anime.setTitle("작품");
    }

    private EpisodeCreateRequest request(int episodeNumber) {
        EpisodeCreateRequest req = new EpisodeCreateRequest();
        req.setEpisodeNumber(episodeNumber);
        req.setTitle(episodeNumber + "화");
        req.setThumbnailUrl("https://img/" + episodeNumber + ".jpg");
        req.setVideoUrl("https://v/" + episodeNumber + ".m3u8");
        req.setDuration(1440);
        return req;
    }

    @Test
    @DisplayName("저장에 성공하면 관심작품 업데이트 알림 트리거가 호출된다")
    void triggersNotificationAfterSave() {
        given(animeRepository.findById(1L)).willReturn(Optional.of(anime));
        given(episodeRepository.findByAnime_Id(1L)).willReturn(List.of());
        given(episodeRepository.saveAndFlush(any(Episode.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.createEpisode(1L, request(1));

        verify(notificationTriggerService).triggerEpisodeUpdateNotification(any(Episode.class));
    }

    @Test
    @DisplayName("같은 작품에 같은 화수가 이미 있으면 400 - 저장도 알림도 없다")
    void duplicateEpisodeNumberIsRejected() {
        given(animeRepository.findById(1L)).willReturn(Optional.of(anime));
        Episode existing = Episode.createEpisode(anime, 1, "1화", "https://img/1.jpg", "https://v/1.m3u8", 1440);
        given(episodeRepository.findByAnime_Id(1L)).willReturn(List.of(existing));

        assertThatThrownBy(() -> service.createEpisode(1L, request(1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이미 등록된 화수입니다");

        verify(episodeRepository, never()).saveAndFlush(any(Episode.class));
        verify(notificationTriggerService, never()).triggerEpisodeUpdateNotification(any(Episode.class));
    }

    @Test
    @DisplayName("존재하지 않는 작품이면 404")
    void unknownAnimeIsRejected() {
        given(animeRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createEpisode(99L, request(1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("애니메이션이 존재하지 않습니다");
    }

    /**
     * 팩토리의 도메인 검증(제목 필수 등)이 500 으로 새어나가면 안 된다.
     */
    @Test
    @DisplayName("필수값이 빠지면 500 이 아니라 400 으로 거절한다")
    void missingRequiredFieldIsBadRequest() {
        given(animeRepository.findById(1L)).willReturn(Optional.of(anime));
        given(episodeRepository.findByAnime_Id(1L)).willReturn(List.of());
        EpisodeCreateRequest req = request(1);
        req.setTitle(null);

        assertThatThrownBy(() -> service.createEpisode(1L, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("제목은 필수입니다");

        verify(notificationTriggerService, never()).triggerEpisodeUpdateNotification(any(Episode.class));
    }

    // ===== 목록 조회 / 수정 =====

    private Episode episodeWithId(long id, int number) {
        Episode episode = Episode.createEpisode(
                anime, number, number + "화", "https://img/" + number + ".jpg", "https://v/" + number + ".m3u8", 1440);
        ReflectionTestUtils.setField(episode, "id", id);
        return episode;
    }

    private EpisodeUpdateRequest updateRequest() {
        return new EpisodeUpdateRequest();
    }

    @Nested
    @DisplayName("화수 목록 조회")
    class ListEpisodes {

        /**
         * 이 트랜잭션은 readOnly 다. 락 걸린 조회를 쓰면 PostgreSQL 이 FOR UPDATE 를 거부해 500 이 난다
         * (큐레이션 단건 조회가 같은 함정으로 죽었다).
         */
        @Test
        @DisplayName("작품 확인에 락 없는 조회를 쓴다 - readOnly 트랜잭션에서 락을 잡으면 500 이다")
        void usesLockFreeAnimeLookup() {
            given(animeRepository.findByIdWithoutLock(1L)).willReturn(Optional.of(anime));
            given(episodeRepository.findByAnime_Id(1L)).willReturn(List.of());

            service.listEpisodes(1L);

            verify(animeRepository, never()).findById(1L);
        }

        @Test
        @DisplayName("화수 오름차순으로 돌려준다")
        void sortsByEpisodeNumber() {
            given(animeRepository.findByIdWithoutLock(1L)).willReturn(Optional.of(anime));
            given(episodeRepository.findByAnime_Id(1L))
                    .willReturn(List.of(episodeWithId(30L, 3), episodeWithId(10L, 1), episodeWithId(20L, 2)));

            List<AdminEpisodeDetailDto> result = service.listEpisodes(1L);

            assertThat(result).extracting(AdminEpisodeDetailDto::getEpisodeNumber)
                    .containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("존재하지 않는 작품이면 404")
        void unknownAnimeIsRejected() {
            given(animeRepository.findByIdWithoutLock(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.listEpisodes(99L))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("애니메이션이 존재하지 않습니다");
        }
    }

    @Nested
    @DisplayName("화수 수정")
    class UpdateEpisode {

        @Test
        @DisplayName("영상 경로를 고칠 수 있다 - 이 API 를 만든 이유다")
        void updatesVideoUrl() {
            Episode episode = episodeWithId(10L, 1);
            given(episodeRepository.findById(10L)).willReturn(Optional.of(episode));
            EpisodeUpdateRequest req = updateRequest();
            req.setVideoUrl("https://v/fixed.m3u8");

            AdminEpisodeDetailDto result = service.updateEpisode(1L, 10L, req);

            assertThat(result.getVideoUrl()).isEqualTo("https://v/fixed.m3u8");
            assertThat(episode.getVideoUrl()).isEqualTo("https://v/fixed.m3u8");
        }

        @Test
        @DisplayName("전달하지 않은 필드는 그대로 둔다 - 부분 수정")
        void leavesUnspecifiedFieldsUntouched() {
            Episode episode = episodeWithId(10L, 1);
            given(episodeRepository.findById(10L)).willReturn(Optional.of(episode));
            EpisodeUpdateRequest req = updateRequest();
            req.setVideoUrl("https://v/fixed.m3u8");

            service.updateEpisode(1L, 10L, req);

            assertThat(episode.getTitle()).isEqualTo("1화");
            assertThat(episode.getThumbnailUrl()).isEqualTo("https://img/1.jpg");
        }

        @Test
        @DisplayName("공개 여부를 켤 수 있다 - false 는 '변경 없음'이 아니다")
        void togglesReleaseFlags() {
            Episode episode = episodeWithId(10L, 1);
            given(episodeRepository.findById(10L)).willReturn(Optional.of(episode));
            EpisodeUpdateRequest req = updateRequest();
            req.setIsReleased(true);
            req.setIsActive(false);

            service.updateEpisode(1L, 10L, req);

            assertThat(episode.getIsReleased()).isTrue();
            assertThat(episode.getIsActive()).isFalse();
        }

        /**
         * not-null 컬럼이라 빈 값이 들어가면 재생이 조용히 깨진다.
         */
        @Test
        @DisplayName("빈 문자열로는 덮어쓸 수 없다 - 400")
        void rejectsBlankValue() {
            Episode episode = episodeWithId(10L, 1);
            given(episodeRepository.findById(10L)).willReturn(Optional.of(episode));
            EpisodeUpdateRequest req = updateRequest();
            req.setVideoUrl("   ");

            assertThatThrownBy(() -> service.updateEpisode(1L, 10L, req))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("빈 값일 수 없습니다");
            assertThat(episode.getVideoUrl()).isEqualTo("https://v/1.m3u8");
        }

        /**
         * 경로의 작품과 실제 소속이 다르면 남의 작품 화수를 고치는 셈이 된다.
         */
        @Test
        @DisplayName("다른 작품의 에피소드는 수정할 수 없다 - 404")
        void rejectsEpisodeOfAnotherAnime() {
            Episode episode = episodeWithId(10L, 1); // anime id 1 소속
            given(episodeRepository.findById(10L)).willReturn(Optional.of(episode));
            EpisodeUpdateRequest req = updateRequest();
            req.setVideoUrl("https://v/hijack.m3u8");

            assertThatThrownBy(() -> service.updateEpisode(999L, 10L, req))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("해당 작품의 에피소드가 아닙니다");
            assertThat(episode.getVideoUrl()).isEqualTo("https://v/1.m3u8");
        }

        @Test
        @DisplayName("존재하지 않는 에피소드면 404")
        void unknownEpisodeIsRejected() {
            given(episodeRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateEpisode(1L, 99L, updateRequest()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("에피소드가 존재하지 않습니다");
        }

        /**
         * 수정은 새 화수가 아니다. 찜한 사용자에게 다시 알리면 스팸이 된다.
         */
        @Test
        @DisplayName("수정에는 알림을 보내지 않는다")
        void doesNotNotifyOnUpdate() {
            Episode episode = episodeWithId(10L, 1);
            given(episodeRepository.findById(10L)).willReturn(Optional.of(episode));
            EpisodeUpdateRequest req = updateRequest();
            req.setTitle("고친 제목");

            service.updateEpisode(1L, 10L, req);

            verify(notificationTriggerService, never()).triggerEpisodeUpdateNotification(any(Episode.class));
        }

        @Test
        @DisplayName("save 를 부르지 않는다 - 영속 엔티티라 더티 체킹으로 반영된다")
        void doesNotCallSave() {
            Episode episode = episodeWithId(10L, 1);
            given(episodeRepository.findById(10L)).willReturn(Optional.of(episode));
            EpisodeUpdateRequest req = updateRequest();
            req.setTitle("고친 제목");

            service.updateEpisode(1L, 10L, req);

            verify(episodeRepository, never()).save(any(Episode.class));
            verify(episodeRepository, never()).saveAndFlush(any(Episode.class));
        }
    }
}
