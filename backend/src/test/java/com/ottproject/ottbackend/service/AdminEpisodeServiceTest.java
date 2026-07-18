package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.admin.EpisodeCreateRequest;
import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.entity.Episode;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.EpisodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

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
}
