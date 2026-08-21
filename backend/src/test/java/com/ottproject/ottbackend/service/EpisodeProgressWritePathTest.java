package com.ottproject.ottbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ottproject.ottbackend.dto.EpisodeDto;
import com.ottproject.ottbackend.dto.EpisodeProgressFlushDto;
import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.entity.EntityTestFixtures;
import com.ottproject.ottbackend.entity.Episode;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.mybatis.EpisodeMapper;
import com.ottproject.ottbackend.mybatis.PlayerProgressQueryMapper;
import com.ottproject.ottbackend.mybatis.PlayerQueryMapper;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.EpisodeRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * episode_progress 쓰기 경로 검증 (실제 PostgreSQL)
 *
 * 왜 이 테스트가 필요한가
 * - 이 테이블의 쓰기는 세 갈래(버퍼 flush 배치 upsert, 값 병합 upsert, 최근본 숨김·삭제)인데
 *   전부 손으로 쓴 SQL 이다. 목으로 검증하면 "매퍼를 불렀다"만 확인되고 행이 어떻게 바뀌는지는 모른다.
 * - 특히 배치 upsert 의 ON CONFLICT ... WHERE 가드가 이 테이블의 유일한 역주행 방어다.
 *   flush 는 10 초에 한 번 도는 배치라 늦게 도착한 스냅샷이 최신 진행률을 덮을 수 있다.
 *   가드가 사라져도 평소에는 아무 증상이 없으므로, 실제 DB 에 두 번 반영해봐야만 확인된다.
 * - 병합 upsert 는 예전에 JPA 조회 후 저장이었다. 쓰기 수단이 둘로 갈려 있던 자리이고
 *   (ARCHITECTURE 3), 조회와 저장 사이의 갱신 유실도 여기 있었다.
 *
 * 왜 H2 가 아니라 Testcontainers 인가
 * - INSERT ... ON CONFLICT DO UPDATE ... WHERE 와 excluded 참조가 PostgreSQL 문법이다.
 *
 * 슬라이스 구성(명시)
 * - 매퍼 XML 을 실제로 실행해야 하므로 JpaSliceTestSupport 의 껍데기 SqlSessionFactory 대신
 *   컨테이너 DataSource 에 붙은 진짜 SqlSessionFactory 를 넣는다. 매퍼 빈 자체는 메인 클래스의
 *   @MapperScan 이 등록한다.
 * - 테스트 메서드를 감싸는 트랜잭션을 끈다(NOT_SUPPORTED). 서비스가 자기 트랜잭션을 열어야
 *   PlayerService 의 클래스 기본값(readOnly)이 실제로 적용되고, 그 안에서 쓰기가 되는지 확인된다.
 *   감싸는 트랜잭션에 얹히면 readOnly 가 무시되어 이 검증이 통째로 무의미해진다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 컨테이너 URL 을 쓰기 위해 자동 대체를 끈다
@Import({EpisodeProgressWritePathTest.MyBatisTestConfig.class, PlayerService.class, RecentAnimeService.class})
@Testcontainers(disabledWithoutDocker = true)
@Tag("testcontainers") // testFast 가 제외하는 태그. 컨테이너를 띄우는 값이 비싸서 편집 직후 되먹임용 실행에서는 뺀다.
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create", // create-drop 이 아니다: 종료 시 drop DDL 이 이미 내려간 컨테이너에 붙으려다 30초를 버린다
            "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
        })
@Transactional(propagation = Propagation.NOT_SUPPORTED) // @DataJpaTest 의 감싸는 트랜잭션을 끈다
class EpisodeProgressWritePathTest {

    @Container
    @SuppressWarnings("resource") // 컨테이너 수명은 Testcontainers 가 관리한다
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    /** 매퍼 XML 을 컨테이너 DataSource 에 붙인다(운영 설정과 같은 위치·네이밍 규칙). */
    @TestConfiguration
    static class MyBatisTestConfig {

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(
                    new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            factory.setTypeAliasesPackage("com.ottproject.ottbackend.dto");
            return factory.getObject();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }

    private static final LocalDateTime EARLIER = LocalDateTime.of(2026, 8, 5, 12, 0, 0);
    private static final LocalDateTime LATER = LocalDateTime.of(2026, 8, 5, 12, 0, 30);

    @Autowired
    private PlayerProgressQueryMapper progressQueryMapper;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private RecentAnimeService recentAnimeService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AnimeRepository animeRepository;

    @Autowired
    private EpisodeRepository episodeRepository;

    @Autowired
    private JdbcTemplate jdbc;

    /** 최근본 서비스가 회차 목록을 얻는 경로. 이 테스트의 관심사는 진행률 행이라 목으로 고정한다. */
    @MockitoBean
    private EpisodeMapper episodeMapper;
    /** 버퍼(Redis)는 슬라이스에 없다. 삭제 시 버퍼 제거를 호출하는지만 본다. */
    @MockitoBean
    private ProgressBufferService progressBuffer;

    @MockitoBean
    private PlaybackAuthService playbackAuthService;

    @MockitoBean
    private PlayerQueryMapper playerQueryMapper;

    private Long userId;
    private Long animeId;
    private Long episodeId;
    private Long otherEpisodeId;

    @BeforeEach
    void setUp() {
        // 감싸는 트랜잭션이 없어 각 테스트의 쓰기가 실제로 커밋된다. 픽스처를 매번 비운다.
        jdbc.execute("truncate table episode_progress, episodes, anime, users cascade");

        userId = userRepository
                .save(User.createLocalUser("progress@example.com", "encoded", "시청자"))
                .getId();
        Anime anime = animeRepository.save(anime("작품"));
        animeId = anime.getId();
        episodeId = episodeRepository.save(episode(anime, 1)).getId();
        otherEpisodeId = episodeRepository.save(episode(anime, 2)).getId();
    }

    /** Anime.createAnime 은 인자가 32개라 픽스처로 쓰기 어렵다. not-null 컬럼만 채운다. */
    private Anime anime(String title) {
        Anime anime = EntityTestFixtures.emptyAnime();
        anime.setTitle(title);
        anime.setStatus(AnimeStatus.ONGOING);
        anime.setAgeRating("ALL");
        anime.setIsExclusive(false);
        anime.setIsNew(false);
        anime.setIsPopular(false);
        anime.setIsCompleted(false);
        anime.setIsSubtitle(true);
        anime.setIsDub(false);
        anime.setIsSimulcast(false);
        anime.setIsActive(true);
        anime.setCurated(false);
        anime.setCurrentEpisodes(0);
        anime.setCreatedAt(EARLIER);
        anime.setUpdatedAt(EARLIER);
        return anime;
    }

    /** 이 슬라이스는 Auditing 을 싣지 않으므로(JpaSliceTestSupport 주석) not-null 시각을 직접 채운다. */
    private Episode episode(Anime anime, int number) {
        Episode episode = EntityTestFixtures.emptyEpisode();
        episode.setAnime(anime);
        episode.setEpisodeNumber(number);
        episode.setTitle(number + "화");
        episode.setThumbnailUrl("https://example.com/thumb.jpg");
        episode.setVideoUrl("https://example.com/video.m3u8");
        episode.setIsActive(true);
        episode.setIsReleased(true);
        episode.setCreatedAt(EARLIER);
        episode.setUpdatedAt(EARLIER);
        return episode;
    }

    private EpisodeProgressFlushDto bufferedRow(Long episodeId, int positionSec, LocalDateTime recordedAt) {
        return EpisodeProgressFlushDto.builder()
                .userId(userId)
                .episodeId(episodeId)
                .positionSec(positionSec)
                .durationSec(1400)
                .updatedAt(recordedAt)
                .build();
    }

    private Map<String, Object> progressRow(Long episodeId) {
        return jdbc.queryForMap(
                "select position_sec, duration_sec, updated_at, hidden_in_recent"
                        + " from episode_progress where user_id = ? and episode_id = ?",
                userId,
                episodeId);
    }

    private int progressRowCount() {
        return jdbc.queryForObject("select count(*) from episode_progress", Integer.class);
    }

    @Nested
    @DisplayName("버퍼 flush 배치 upsert")
    class BatchUpsert {

        @Test
        @DisplayName("한 문장으로 여러 행을 새로 만든다")
        void insertsEveryRowInOneStatement() {
            progressQueryMapper.upsertProgressBatch(
                    List.of(bufferedRow(episodeId, 100, EARLIER), bufferedRow(otherEpisodeId, 200, EARLIER)));

            assertThat(progressRowCount()).isEqualTo(2);
            assertThat(progressRow(episodeId)).containsEntry("position_sec", 100);
            assertThat(progressRow(otherEpisodeId)).containsEntry("position_sec", 200);
            // 숨김 여부는 신규 행 기본값이어야 한다 — 반영이 최근본 목록에서 작품을 지워버리면 안 된다.
            assertThat(progressRow(episodeId)).containsEntry("hidden_in_recent", false);
        }

        @Test
        @DisplayName("같은 사용자×회차가 다시 오면 갱신한다 - 유니크 제약 충돌로 실패하지 않는다")
        void updatesExistingRow() {
            progressQueryMapper.upsertProgressBatch(List.of(bufferedRow(episodeId, 100, EARLIER)));

            progressQueryMapper.upsertProgressBatch(List.of(bufferedRow(episodeId, 900, LATER)));

            assertThat(progressRowCount()).isEqualTo(1);
            assertThat(progressRow(episodeId)).containsEntry("position_sec", 900);
        }

        /**
         * 이 테스트가 이 파일의 존재 이유다.
         * upsertProgressBatch 의 `where episode_progress.updated_at <= excluded.updated_at` 를 지우면 여기서 잡힌다.
         * (실제로 지우고 빨간불을 확인했다)
         */
        @Test
        @DisplayName("늦게 도착한 버퍼 값은 최신 진행률을 되돌리지 못한다 - 이어보기 위치 역주행 방지")
        void staleRowDoesNotMoveProgressBackwards() {
            progressQueryMapper.upsertProgressBatch(List.of(bufferedRow(episodeId, 900, LATER)));

            // 앞선 flush 가 실패해 남아 있던 오래된 스냅샷이 뒤늦게 반영되는 상황
            progressQueryMapper.upsertProgressBatch(List.of(bufferedRow(episodeId, 100, EARLIER)));

            Map<String, Object> row = progressRow(episodeId);
            assertThat(row).containsEntry("position_sec", 900);
            assertThat(row).containsEntry("updated_at", Timestamp.valueOf(LATER)); // 시각도 되돌아가면 안 된다
        }
    }

    @Nested
    @DisplayName("값 병합 upsert(위치·길이 중 하나만 도착)")
    class MergeUpsert {

        /**
         * PlayerService 는 클래스 기본값이 @Transactional(readOnly = true) 다.
         * readOnly 트랜잭션은 JDBC 커넥션까지 read-only 로 세팅되므로, 쓰기 메서드에 @Transactional 을
         * 다시 붙이지 않으면 이 upsert 가 DB 에서 거부된다. 그것을 여기서 실제로 확인한다.
         */
        @Test
        @DisplayName("기존 위치를 지키고 길이만 갱신한다 - 위치가 무효값으로 왔을 때")
        void keepsCurrentPositionWhenPositionIsInvalid() {
            progressQueryMapper.upsertProgressBatch(List.of(bufferedRow(episodeId, 700, EARLIER)));

            playerService.saveProgress(userId, episodeId, -5, 1500);

            Map<String, Object> row = progressRow(episodeId);
            assertThat(row).containsEntry("position_sec", 700);
            assertThat(row).containsEntry("duration_sec", 1500);
        }

        @Test
        @DisplayName("기존 길이를 지키고 위치만 갱신한다 - 길이가 0 으로 왔을 때")
        void keepsCurrentDurationWhenDurationIsNonPositive() {
            progressQueryMapper.upsertProgressBatch(List.of(bufferedRow(episodeId, 700, EARLIER)));

            playerService.saveProgress(userId, episodeId, 800, 0);

            Map<String, Object> row = progressRow(episodeId);
            assertThat(row).containsEntry("position_sec", 800);
            assertThat(row).containsEntry("duration_sec", 1400);
        }

        @Test
        @DisplayName("위치는 총 길이를 넘지 못한다 - 진행률이 100% 를 넘으면 안 된다")
        void clampsPositionToStoredDuration() {
            progressQueryMapper.upsertProgressBatch(List.of(bufferedRow(episodeId, 700, EARLIER)));

            playerService.saveProgress(userId, episodeId, 9999, null);

            assertThat(progressRow(episodeId)).containsEntry("position_sec", 1400);
        }

        /**
         * 신규 행은 길이가 0 으로 시작하고 보정이 위치를 0 으로 되돌린다.
         * JPA 로 저장하던 때와 같은 결과다 — 수단만 바꾸고 동작은 그대로 뒀다.
         * 바꾸려면 "0 = 길이 미상" 을 보정에서 제외해야 한다.
         */
        @Test
        @DisplayName("총 길이 없이 새 진행률을 저장하면 위치가 0 으로 보정된다 - 현재 동작")
        void clampsToZeroWhenDurationUnknownOnCreate() {
            playerService.saveProgress(userId, episodeId, 300, null);

            Map<String, Object> row = progressRow(episodeId);
            assertThat(row).containsEntry("position_sec", 0);
            assertThat(row).containsEntry("duration_sec", 0);
            assertThat(row).containsEntry("hidden_in_recent", false);
        }

        /**
         * 없는 사용자·에피소드는 외래 키가 거른다. 이 제약이 빠지면 참조 대상 없는 진행률 행이 남고,
         * 시청 기록 조회가 조인에서 통째로 사라진다.
         */
        @Test
        @DisplayName("없는 에피소드의 진행률은 외래 키가 거부한다")
        void foreignKeyRejectsUnknownEpisode() {
            Throwable thrown = catchThrowable(() -> playerService.saveProgress(userId, 999_999L, 300, null));

            assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(progressRowCount()).isZero();
        }
    }

    @Nested
    @DisplayName("최근본 목록 숨김·삭제")
    class RecentAnime {

        @Test
        @DisplayName("숨김은 그 작품의 회차만 표시에서 빼고 시청 기록은 남긴다")
        void hideMarksOnlyRequestedEpisodes() {
            progressQueryMapper.upsertProgressBatch(
                    List.of(bufferedRow(episodeId, 100, EARLIER), bufferedRow(otherEpisodeId, 200, EARLIER)));
            given(episodeMapper.findEpisodesByAnimeId(animeId))
                    .willReturn(List.of(EpisodeDto.builder().id(episodeId).build()));

            recentAnimeService.hideFromRecent(userId, animeId);

            assertThat(progressRow(episodeId)).containsEntry("hidden_in_recent", true);
            assertThat(progressRow(episodeId)).containsEntry("position_sec", 100); // 기록 자체는 그대로
            assertThat(progressRow(otherEpisodeId)).containsEntry("hidden_in_recent", false);
        }

        @Test
        @DisplayName("정주행 삭제는 그 작품의 회차 진행률만 지우고 버퍼도 함께 비운다")
        void deleteRemovesRowsAndEvictsBuffer() {
            progressQueryMapper.upsertProgressBatch(
                    List.of(bufferedRow(episodeId, 100, EARLIER), bufferedRow(otherEpisodeId, 200, EARLIER)));
            given(episodeMapper.findEpisodesByAnimeId(animeId))
                    .willReturn(List.of(EpisodeDto.builder().id(episodeId).build()));

            recentAnimeService.deleteFromBinge(userId, animeId);

            assertThat(progressRowCount()).isEqualTo(1);
            assertThat(progressRow(otherEpisodeId)).containsEntry("position_sec", 200);
            // 버퍼에 남아 있으면 다음 flush 가 지운 행을 되살린다
            verify(progressBuffer).evict(userId, List.of(episodeId));
        }
    }
}
