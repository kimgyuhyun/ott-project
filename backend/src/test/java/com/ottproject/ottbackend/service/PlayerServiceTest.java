package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.EpisodeProgressResponseDto;
import com.ottproject.ottbackend.entity.Episode;
import com.ottproject.ottbackend.entity.EpisodeProgress;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.SkipType;
import com.ottproject.ottbackend.mybatis.EpisodeMapper;
import com.ottproject.ottbackend.mybatis.PlayerQueryMapper;
import com.ottproject.ottbackend.repository.EpisodeProgressRepository;
import com.ottproject.ottbackend.repository.EpisodeRepository;
import com.ottproject.ottbackend.repository.EpisodeSkipMetaRepository;
import com.ottproject.ottbackend.repository.SkipUsageRepository;
import com.ottproject.ottbackend.repository.SubtitleRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * PlayerService 시청 진행률/스킵 로깅 검증
 *
 * 왜 이 테스트가 필요한가
 * - saveProgress 는 이어보기의 근거 데이터를 만든다. 위치 보정과 값 무시 규칙이 유일한 실로직이다.
 * - 문자열 스킵 타입 파싱은 알 수 없는 값을 조용히 버리는데, 이 방어가 사라지면 클라이언트 오타 하나로 500 이 난다.
 */
@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock private SubtitleRepository subtitleRepository;
    @Mock private EpisodeSkipMetaRepository skipMetaRepository;
    @Mock private SkipUsageRepository skipUsageRepository;
    @Mock private UserRepository userRepository;
    @Mock private EpisodeRepository episodeRepository;
    @Mock private EpisodeProgressRepository progressRepository;
    @Mock private EpisodeMapper episodeMapper;
    @Mock private PlayerQueryMapper playerQueryMapper;
    @Mock private PlaybackAuthService playbackAuthService;

    @InjectMocks private PlayerService playerService;

    private static final Long USER_ID = 1L;
    private static final Long EPISODE_ID = 10L;

    private User user;
    private Episode episode;

    @BeforeEach
    void setUp() {
        user = User.createLocalUser("viewer@example.com", "password", "시청자");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        episode = new Episode();
        ReflectionTestUtils.setField(episode, "id", EPISODE_ID);
    }

    /**
     * DB 에 이미 있는 진행률(durationSec 은 not null 이라 항상 값이 있다).
     */
    private EpisodeProgress existingProgress(int positionSec, int durationSec) {
        EpisodeProgress progress = EpisodeProgress.createProgress(user, episode, positionSec);
        progress.setDurationSec(durationSec);
        return progress;
    }

    private EpisodeProgress captureSaved() {
        ArgumentCaptor<EpisodeProgress> captor = ArgumentCaptor.forClass(EpisodeProgress.class);
        verify(progressRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("saveProgress")
    class SaveProgress {

        @Test
        @DisplayName("기존 진행률이 있으면 새로 만들지 않고 위치를 갱신한다")
        void updatesExistingProgress() {
            EpisodeProgress existing = existingProgress(100, 1400);
            given(progressRepository.findByUser_IdAndEpisode_Id(USER_ID, EPISODE_ID))
                    .willReturn(Optional.of(existing));

            playerService.saveProgress(USER_ID, EPISODE_ID, 500, 1400);

            EpisodeProgress saved = captureSaved();
            assertThat(saved).isSameAs(existing);
            assertThat(saved.getPositionSec()).isEqualTo(500);
            verify(userRepository, never()).findById(USER_ID);
        }

        @Test
        @DisplayName("진행률이 없으면 사용자/에피소드를 찾아 새로 만든다")
        void createsProgressWhenAbsent() {
            given(progressRepository.findByUser_IdAndEpisode_Id(USER_ID, EPISODE_ID)).willReturn(Optional.empty());
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            given(episodeRepository.findById(EPISODE_ID)).willReturn(Optional.of(episode));

            playerService.saveProgress(USER_ID, EPISODE_ID, 300, 1400);

            EpisodeProgress saved = captureSaved();
            assertThat(saved.getUser()).isSameAs(user);
            assertThat(saved.getEpisode()).isSameAs(episode);
            assertThat(saved.getPositionSec()).isEqualTo(300);
            assertThat(saved.getDurationSec()).isEqualTo(1400);
        }

        @Test
        @DisplayName("위치가 총 길이를 넘으면 총 길이로 잘라 저장한다 - 진행률이 100% 를 넘으면 안 된다")
        void clampsPositionToDuration() {
            EpisodeProgress existing = existingProgress(0, 1400);
            given(progressRepository.findByUser_IdAndEpisode_Id(USER_ID, EPISODE_ID))
                    .willReturn(Optional.of(existing));

            playerService.saveProgress(USER_ID, EPISODE_ID, 9999, 1400);

            assertThat(captureSaved().getPositionSec()).isEqualTo(1400);
        }

        @Test
        @DisplayName("음수 위치는 무시하고 기존 위치를 지킨다")
        void ignoresNegativePosition() {
            EpisodeProgress existing = existingProgress(700, 1400);
            given(progressRepository.findByUser_IdAndEpisode_Id(USER_ID, EPISODE_ID))
                    .willReturn(Optional.of(existing));

            playerService.saveProgress(USER_ID, EPISODE_ID, -5, 1400);

            assertThat(captureSaved().getPositionSec()).isEqualTo(700);
        }

        @Test
        @DisplayName("위치가 없으면(null) 기존 위치를 지킨다")
        void ignoresNullPosition() {
            EpisodeProgress existing = existingProgress(700, 1400);
            given(progressRepository.findByUser_IdAndEpisode_Id(USER_ID, EPISODE_ID))
                    .willReturn(Optional.of(existing));

            playerService.saveProgress(USER_ID, EPISODE_ID, null, 1400);

            assertThat(captureSaved().getPositionSec()).isEqualTo(700);
        }

        @Test
        @DisplayName("0 이하의 총 길이는 무시하고 기존 길이를 지킨다 - 길이 0 은 재생 불가를 뜻하지 않는다")
        void ignoresNonPositiveDuration() {
            EpisodeProgress existing = existingProgress(700, 1400);
            given(progressRepository.findByUser_IdAndEpisode_Id(USER_ID, EPISODE_ID))
                    .willReturn(Optional.of(existing));

            playerService.saveProgress(USER_ID, EPISODE_ID, 800, 0);

            EpisodeProgress saved = captureSaved();
            assertThat(saved.getDurationSec()).isEqualTo(1400);
            assertThat(saved.getPositionSec()).isEqualTo(800);
        }

        /**
         * 신규 진행률은 durationSec 이 0 으로 시작한다(createProgress 기본값).
         * 이때 총 길이를 같이 보내지 않으면 보정 로직이 위치를 0 으로 되돌려 시청 위치가 버려진다.
         * 현재 동작을 그대로 고정해 둔다 — 바꾸려면 "0 = 길이 미상" 을 보정에서 제외해야 한다.
         */
        @Test
        @DisplayName("총 길이 없이 새 진행률을 저장하면 위치가 0 으로 보정된다 - 현재 동작")
        void clampsToZeroWhenDurationUnknownOnCreate() {
            given(progressRepository.findByUser_IdAndEpisode_Id(USER_ID, EPISODE_ID)).willReturn(Optional.empty());
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            given(episodeRepository.findById(EPISODE_ID)).willReturn(Optional.of(episode));

            playerService.saveProgress(USER_ID, EPISODE_ID, 300, null);

            EpisodeProgress saved = captureSaved();
            assertThat(saved.getDurationSec()).isZero();
            assertThat(saved.getPositionSec()).isZero();
        }

        @Test
        @DisplayName("사용자나 에피소드가 없으면 진행률을 만들지 않는다")
        void rejectsUnknownUserOrEpisode() {
            given(progressRepository.findByUser_IdAndEpisode_Id(USER_ID, EPISODE_ID)).willReturn(Optional.empty());
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> playerService.saveProgress(USER_ID, EPISODE_ID, 300, 1400))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(progressRepository, never()).save(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("trackUsage(String)")
    class TrackUsageByName {

        @Test
        @DisplayName("대소문자와 무관하게 스킵 타입을 해석한다")
        void parsesTypeCaseInsensitively() {
            given(episodeRepository.findById(EPISODE_ID)).willReturn(Optional.of(episode));
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

            playerService.trackUsage(USER_ID, EPISODE_ID, "intro", 30);

            verify(skipUsageRepository).save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("알 수 없는 스킵 타입은 조용히 버린다 - 클라이언트 오타로 500 이 나면 안 된다")
        void ignoresUnknownType() {
            playerService.trackUsage(USER_ID, EPISODE_ID, "nonsense", 30);

            verify(skipUsageRepository, never()).save(org.mockito.ArgumentMatchers.any());
            verify(episodeRepository, never()).findById(EPISODE_ID);
        }

        @Test
        @DisplayName("스킵 타입이 null 이면 아무것도 기록하지 않는다")
        void ignoresNullType() {
            playerService.trackUsage(USER_ID, EPISODE_ID, (String) null, 30);

            verify(skipUsageRepository, never()).save(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("trackUsage(SkipType)")
    class TrackUsageByEnum {

        @Test
        @DisplayName("비로그인 사용자의 스킵도 사용자 없이 기록한다")
        void recordsAnonymousUsage() {
            given(episodeRepository.findById(EPISODE_ID)).willReturn(Optional.of(episode));

            playerService.trackUsage(null, EPISODE_ID, SkipType.INTRO, 30);

            verify(skipUsageRepository).save(org.mockito.ArgumentMatchers.any());
            verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("getBulkProgress")
    class BulkProgress {

        @Test
        @DisplayName("에피소드 ID 를 키로 진행률을 묶어 돌려준다")
        void keysResultByEpisodeId() {
            Episode other = new Episode();
            ReflectionTestUtils.setField(other, "id", 20L);
            EpisodeProgress first = existingProgress(100, 1400);
            EpisodeProgress second = EpisodeProgress.createProgress(user, other, 200);
            second.setDurationSec(1500);
            given(progressRepository.findByUser_IdAndEpisode_IdIn(USER_ID, List.of(EPISODE_ID, 20L)))
                    .willReturn(List.of(first, second));

            Map<Long, EpisodeProgressResponseDto> result =
                    playerService.getBulkProgress(USER_ID, List.of(EPISODE_ID, 20L));

            assertThat(result).hasSize(2);
            assertThat(result.get(EPISODE_ID).getPositionSec()).isEqualTo(100);
            assertThat(result.get(20L).getPositionSec()).isEqualTo(200);
        }

        @Test
        @DisplayName("진행률이 없으면 빈 결과를 준다")
        void returnsEmptyMapWhenNoProgress() {
            given(progressRepository.findByUser_IdAndEpisode_IdIn(USER_ID, List.of(EPISODE_ID)))
                    .willReturn(List.of());

            assertThat(playerService.getBulkProgress(USER_ID, List.of(EPISODE_ID))).isEmpty();
        }
    }
}
