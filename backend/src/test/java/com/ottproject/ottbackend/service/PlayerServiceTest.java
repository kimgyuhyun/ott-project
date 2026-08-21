package com.ottproject.ottbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ottproject.ottbackend.dto.EpisodeProgressResponseDto;
import com.ottproject.ottbackend.entity.EntityTestFixtures;
import com.ottproject.ottbackend.entity.Episode;
import com.ottproject.ottbackend.entity.EpisodeProgress;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.SkipType;
import com.ottproject.ottbackend.mybatis.EpisodeMapper;
import com.ottproject.ottbackend.mybatis.PlayerProgressQueryMapper;
import com.ottproject.ottbackend.mybatis.PlayerQueryMapper;
import com.ottproject.ottbackend.repository.EpisodeProgressRepository;
import com.ottproject.ottbackend.repository.EpisodeRepository;
import com.ottproject.ottbackend.repository.EpisodeSkipMetaRepository;
import com.ottproject.ottbackend.repository.SkipUsageRepository;
import com.ottproject.ottbackend.repository.SubtitleRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * PlayerService 시청 진행률/스킵 로깅 검증
 *
 * 왜 이 테스트가 필요한가
 * - saveProgress 는 이어보기의 근거 데이터를 만든다. 어느 경로로 보낼지 고르고 무효값을 걸러내는 것이
 *   이 서비스의 실로직이고, 값 병합 자체는 SQL 이 한다(EpisodeProgressWritePathTest 가 검증).
 * - 문자열 스킵 타입 파싱은 알 수 없는 값을 조용히 버리는데, 이 방어가 사라지면 클라이언트 오타 하나로 500 이 난다.
 */
@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private SubtitleRepository subtitleRepository;

    @Mock
    private EpisodeSkipMetaRepository skipMetaRepository;

    @Mock
    private SkipUsageRepository skipUsageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EpisodeRepository episodeRepository;

    @Mock
    private EpisodeProgressRepository progressRepository;

    @Mock
    private EpisodeMapper episodeMapper;

    @Mock
    private PlayerProgressQueryMapper progressQueryMapper;

    @Mock
    private PlayerQueryMapper playerQueryMapper;

    @Mock
    private PlaybackAuthService playbackAuthService;

    @Mock
    private ProgressBufferService progressBuffer;

    @InjectMocks
    private PlayerService playerService;

    private static final Long USER_ID = 1L;
    private static final Long EPISODE_ID = 10L;

    private User user;
    private Episode episode;

    @BeforeEach
    void setUp() {
        user = User.createLocalUser("viewer@example.com", "password", "시청자");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        episode = EntityTestFixtures.emptyEpisode();
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

    /**
     * 병합 경로가 매퍼로 내려보낸 값. 병합 결과 자체는 SQL 이 정하므로 여기서는 인자만 본다
     * (실제 병합 결과는 EpisodeProgressWritePathTest 가 실제 PostgreSQL 에서 검증한다).
     */
    private void verifyMergedWith(Integer positionSec, Integer durationSec) {
        verify(progressQueryMapper)
                .mergeProgress(eq(USER_ID), eq(EPISODE_ID), eq(positionSec), eq(durationSec), any(LocalDateTime.class));
    }

    @Nested
    @DisplayName("saveProgress")
    class SaveProgress {

        /**
         * 위치·길이가 모두 유효하면 이전 값을 볼 필요가 없다.
         * 이 경로에서 DB 를 건드리면 write-back 의 목적(커넥션 점유 제거)이 사라지므로 함께 고정한다.
         */
        @Test
        @DisplayName("위치와 길이가 모두 유효하면 DB 없이 버퍼에만 쓴다")
        void writesToBufferWithoutTouchingDb() {
            playerService.saveProgress(USER_ID, EPISODE_ID, 500, 1400);

            verify(progressBuffer).write(USER_ID, EPISODE_ID, 500, 1400);
            verify(progressRepository, never()).findByUser_IdAndEpisode_Id(USER_ID, EPISODE_ID);
            verify(progressQueryMapper, never()).mergeProgress(any(), any(), any(), any(), any());
            verify(userRepository, never()).findById(USER_ID);
        }

        @Test
        @DisplayName("위치가 총 길이를 넘으면 총 길이로 잘라 버퍼에 쓴다 - 진행률이 100% 를 넘으면 안 된다")
        void clampsPositionToDuration() {
            playerService.saveProgress(USER_ID, EPISODE_ID, 9999, 1400);

            verify(progressBuffer).write(USER_ID, EPISODE_ID, 1400, 1400);
        }

        /**
         * 값이 불완전하면 병합이 필요하다. 병합은 한 문장(INSERT ... ON CONFLICT)이 하므로
         * 서비스는 이전 값을 읽지 않는다 — 읽고 쓰는 사이에 끼어든 갱신이 유실되던 자리다.
         */
        @Test
        @DisplayName("병합이 필요하면 이전 값을 읽지 않고 매퍼 한 문장으로 내려보낸다")
        void mergesWithoutReadingCurrentValue() {
            playerService.saveProgress(USER_ID, EPISODE_ID, 300, null);

            verifyMergedWith(300, null);
            verify(progressRepository, never()).findByUser_IdAndEpisode_Id(USER_ID, EPISODE_ID);
        }

        @Test
        @DisplayName("음수 위치는 무시한다 - 기존 위치를 지키도록 null 로 내려보낸다")
        void ignoresNegativePosition() {
            playerService.saveProgress(USER_ID, EPISODE_ID, -5, 1400);

            verifyMergedWith(null, 1400);
        }

        @Test
        @DisplayName("위치가 없으면(null) 그대로 null 로 내려보낸다 - 기존 위치 유지")
        void ignoresNullPosition() {
            playerService.saveProgress(USER_ID, EPISODE_ID, null, 1400);

            verifyMergedWith(null, 1400);
        }

        @Test
        @DisplayName("0 이하의 총 길이는 무시한다 - 길이 0 은 재생 불가를 뜻하지 않는다")
        void ignoresNonPositiveDuration() {
            playerService.saveProgress(USER_ID, EPISODE_ID, 800, 0);

            verifyMergedWith(800, null);
        }

        /**
         * 위치·길이 둘 다 비어 있어도 요청은 버려지지 않는다. 넘길 값이 없으면 병합문이
         * 기존 행을 그대로 두고 시각만 갱신한다(없으면 0/0 행을 만든다).
         */
        @Test
        @DisplayName("위치·길이가 모두 비어도 매퍼로 내려보낸다")
        void mergesEvenWhenBothValuesAreMissing() {
            playerService.saveProgress(USER_ID, EPISODE_ID, null, null);

            verifyMergedWith(null, null);
        }

        /**
         * 예전에는 행이 없으면 사용자·에피소드를 조회해 엔티티를 만들었다(JPA 쓰기).
         * 지금은 존재 확인 없이 upsert 한 문장으로 끝내고, 없는 대상은 외래 키가 거른다
         * (FK 거부는 EpisodeProgressWritePathTest 가 실제 DB 에서 확인한다).
         */
        @Test
        @DisplayName("사용자·에피소드를 조회하지 않는다 - 없는 대상은 외래 키가 거른다")
        void doesNotLookUpUserOrEpisode() {
            playerService.saveProgress(USER_ID, EPISODE_ID, 300, null);

            verify(userRepository, never()).findById(USER_ID);
            verify(episodeRepository, never()).findById(EPISODE_ID);
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
            Episode other = EntityTestFixtures.emptyEpisode();
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

            assertThat(playerService.getBulkProgress(USER_ID, List.of(EPISODE_ID)))
                    .isEmpty();
        }
    }
}
