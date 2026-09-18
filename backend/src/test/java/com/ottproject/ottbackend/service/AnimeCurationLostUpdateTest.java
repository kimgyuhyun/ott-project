package com.ottproject.ottbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

import com.ottproject.ottbackend.config.QuerydslConfig;
import com.ottproject.ottbackend.dto.admin.AdminAnimeDetailDto;
import com.ottproject.ottbackend.dto.admin.AnimeBulkCurationRequest;
import com.ottproject.ottbackend.dto.admin.AnimeCurationSearchCondition;
import com.ottproject.ottbackend.dto.admin.AnimeCurationUpdateRequest;
import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.entity.EntityTestFixtures;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.exception.AnimeVersionConflictException;
import com.ottproject.ottbackend.mybatis.RatingQueryMapper;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.JpaSliceTestSupport;
import com.ottproject.ottbackend.repository.RatingRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import com.ottproject.ottbackend.repository.curation.AnimeCurationQueryRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
 * 관리자 단건 큐레이션 수정의 갱신 분실 방어 검증 (실제 PostgreSQL, 실제 커밋)
 *
 * 결함
 * - update 는 findById 의 PESSIMISTIC_WRITE 로 행을 잠그지만, 그 락은 트랜잭션 하나 동안만 유지된다.
 *   관리자가 수정 폼을 연 조회 요청과 저장 요청은 서로 다른 트랜잭션이라 그 사이를 지켜주지 못한다.
 * - 흐름: A 가 폼을 연다 → B 가 같은 작품을 읽고 제목을 고쳐 저장 → A 가 옛 화면 기준으로 제목을 저장
 *   → B 의 제목이 조용히 사라진다. 방어: 폼이 본 version 을 요청에 싣고, 현재 행과 다르면 거절한다.
 *
 * 왜 같은 필드(제목)를 두 번 고치는가
 * - 프론트(AnimeCurationEditModal.buildRequest)는 폼 전체가 아니라 원본과 달라진 필드만 보낸다.
 *   그래서 서로 다른 필드를 고친 두 저장은 덮어쓰지 않는다. 분실은 같은 필드를 고쳤을 때 드러난다.
 *
 * 스레드가 없는 이유
 * - 이 결함은 두 트랜잭션이 겹칠 때가 아니라 요청 사이에서 일어난다. 겹치는 쪽은 비관적 락이 이미
 *   직렬화한다. 그래서 요청을 순서대로 흉내 내고, 각 호출이 독립 트랜잭션으로 커밋되게만 한다.
 *
 * 테스트 환경(ARCHITECTURE 15절)
 * - RefundIdempotencyTest 와 같은 슬라이스 구성: 실제 PostgreSQL, 서비스는 실물, 감싸는 트랜잭션 끔.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 컨테이너 URL 을 쓰기 위해 자동 대체를 끈다
@Import({
    JpaSliceTestSupport.class,
    QuerydslConfig.class,
    AnimeCurationQueryRepository.class, // 벌크 경로를 실제 SQL 로 태운다(@DataJpaTest 는 일반 @Repository 를 스캔하지 않는다)
    AnimeCurationService.class,
    RatingService.class // 사용자 별점이 같은 행의 집계 컬럼을 쓴다
})
@Testcontainers(disabledWithoutDocker = true)
@Tag("testcontainers") // testFast 가 제외하는 태그
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create", // create-drop 이 아니다: 종료 시 drop DDL 이 이미 내려간 컨테이너에 붙으려다 30초를 버린다
            "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
        })
@Transactional(propagation = Propagation.NOT_SUPPORTED) // @DataJpaTest 의 감싸는 트랜잭션을 끈다 — 호출마다 실제로 커밋돼야 한다
class AnimeCurationLostUpdateTest {

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

    @Autowired
    private AnimeCurationService service;

    @Autowired
    private AnimeRepository animeRepository;

    @Autowired
    private RatingService ratingService;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private RatingQueryMapper ratingQueryMapper; // 집계 조회는 MyBatis 다. 이 테스트의 관심사는 쓰기 쪽이다

    @MockitoBean
    private AnimeCacheService animeCacheService; // Redis 캐시 무효화. 이 결함과 무관하다

    private Long animeId;
    private Long userId;

    @BeforeEach
    void setUp() {
        ratingRepository.deleteAll();
        animeRepository.deleteAll();
        userRepository.deleteAll();
        animeId = animeRepository.save(anime("원래 제목")).getId();
        userId = userRepository
                .save(User.createLocalUser("rater@example.com", "encoded", "별점러"))
                .getId();
        given(ratingQueryMapper.findAverageRatingByAnimeId(animeId)).willReturn(4.0);
        given(ratingQueryMapper.countRatingsByAnimeId(animeId)).willReturn(1L);
    }

    /** not-null 컬럼만 채운 최소 엔티티(AnimeCurationQueryRepositoryTest 와 같은 방식) */
    private Anime anime(String title) {
        LocalDateTime now = LocalDateTime.now();
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
        anime.setYear(2026); // 벌크 조건으로 쓴다
        anime.setCurated(false);
        anime.setCurrentEpisodes(0);
        anime.setCreatedAt(now); // 슬라이스에는 Auditing 이 없어 직접 채운다
        anime.setUpdatedAt(now);
        return anime;
    }

    private AnimeCurationUpdateRequest titleChange(String title, Long seenVersion) {
        AnimeCurationUpdateRequest request = new AnimeCurationUpdateRequest();
        request.setTitle(title);
        request.setVersion(seenVersion);
        return request;
    }

    private Anime current() {
        return animeRepository.findByIdWithoutLock(animeId).orElseThrow();
    }

    /**
     * 방어 전(커밋 194b472)에는 A 의 저장이 성공하고 최종 제목이 "A 제목" 이었다. 그 두 단언이 뒤집힌 것이 이 테스트다.
     * 충돌은 서비스의 version 비교(AnimeVersionConflictException)가 먼저 잡고, 그 비교와 커밋 사이에 끼어든
     * 경합은 Hibernate 의 UPDATE ... WHERE version=? (ObjectOptimisticLockingFailureException)이 잡는다. 둘 다 409 다.
     */
    @Test
    @DisplayName("옛 화면 기준 저장은 거절되고 먼저 저장한 관리자의 제목이 남는다")
    void laterSaveFromStaleFormIsRejected() {
        // 1) 두 관리자가 같은 시점의 상태로 수정 폼을 연다(조회 요청은 여기서 끝난다)
        AdminAnimeDetailDto seenByA = service.get(animeId);
        AdminAnimeDetailDto seenByB = service.get(animeId);
        assertThat(seenByA.getVersion()).isEqualTo(seenByB.getVersion());

        // 2) B 가 먼저 저장해 커밋한다
        service.update(animeId, titleChange("B 제목", seenByB.getVersion()));
        assertThat(current().getTitle()).isEqualTo("B 제목");

        // 3) A 가 옛 화면 기준으로 저장한다 — B 의 수정을 본 적이 없다
        Throwable thrown = catchThrowable(() -> service.update(animeId, titleChange("A 제목", seenByA.getVersion())));

        assertThat(thrown)
                .as("A 의 저장이 거절돼야 한다")
                .isInstanceOfAny(AnimeVersionConflictException.class, ObjectOptimisticLockingFailureException.class);
        assertThat(current().getTitle()).as("B 의 수정이 남아야 한다").isEqualTo("B 제목");
    }

    @Test
    @DisplayName("같은 version 으로 두 번 저장하면 두 번째는 거절된다")
    void secondSaveWithSameVersionIsRejected() {
        Long seen = service.get(animeId).getVersion();

        service.update(animeId, titleChange("첫 저장", seen));
        Throwable thrown = catchThrowable(() -> service.update(animeId, titleChange("두 번째 저장", seen)));

        assertThat(thrown).isInstanceOf(AnimeVersionConflictException.class);
        assertThat(current().getTitle()).isEqualTo("첫 저장");
    }

    /**
     * 응답의 version 은 커밋 후 DB 값과 같아야 한다. 옛 값이 실리면 같은 폼에서 이어서 저장할 때 자기 자신과 충돌한다.
     */
    @Test
    @DisplayName("저장 응답의 version 은 1 올라가 있고, 그 값으로 이어서 저장할 수 있다")
    void responseCarriesIncrementedVersion() {
        Long seen = service.get(animeId).getVersion();

        AdminAnimeDetailDto saved = service.update(animeId, titleChange("첫 저장", seen));

        assertThat(saved.getVersion()).isEqualTo(seen + 1);
        assertThat(current().getVersion()).isEqualTo(seen + 1);

        AdminAnimeDetailDto savedAgain = service.update(animeId, titleChange("이어서 저장", saved.getVersion()));
        assertThat(savedAgain.getVersion()).isEqualTo(seen + 2);
        assertThat(current().getTitle()).isEqualTo("이어서 저장");
    }

    /**
     * 벌크 큐레이션은 QueryDSL 벌크 UPDATE 라 하이버네이트가 @Version 을 올려주지 않는다. 리포지토리가
     * 직접 올리지 않으면, 벌크 직전에 폼을 연 관리자가 옛 version 으로 저장해 벌크 결과를 덮어쓴다.
     *
     * 방어 전(커밋 1bcc4af)에는 저장이 통과하고 최종 isPopular 가 false 였다. 그 두 단언이 뒤집힌 것이 이 테스트다.
     */
    @Test
    @DisplayName("벌크 뒤 옛 폼 저장은 거절되고 벌크가 켠 배지가 남는다")
    void bulkCurationDoesNotBumpVersion() {
        // 1) 관리자가 수정 폼을 연다(isPopular=false 인 상태)
        AdminAnimeDetailDto seen = service.get(animeId);
        assertThat(seen.getIsPopular()).isFalse();

        // 2) 그 사이 벌크 큐레이션이 같은 작품의 배지를 켠다
        assertThat(service.applyBulkCuration(popularBulkRequest())).isEqualTo(1L);
        assertThat(current().getIsPopular()).isTrue();

        // 3) 관리자가 옛 화면 기준으로 저장한다 — 벌크가 켠 것을 본 적이 없다
        AnimeCurationUpdateRequest request = new AnimeCurationUpdateRequest();
        request.setIsPopular(false);
        request.setVersion(seen.getVersion());
        Throwable thrown = catchThrowable(() -> service.update(animeId, request));

        assertThat(thrown).as("옛 폼 저장이 거절돼야 한다").isInstanceOf(AnimeVersionConflictException.class);
        assertThat(current().getIsPopular()).as("벌크가 켠 배지가 남아야 한다").isTrue();
        assertThat(current().getVersion()).as("벌크도 version 을 올린다").isEqualTo(seen.getVersion() + 1);
    }

    /** year=2026 조건으로 isPopular 를 켜는 벌크 요청(대상 1건) */
    private AnimeBulkCurationRequest popularBulkRequest() {
        AnimeCurationSearchCondition condition = new AnimeCurationSearchCondition();
        condition.setYear(2026);
        AnimeBulkCurationRequest bulk = new AnimeBulkCurationRequest();
        bulk.setCondition(condition);
        bulk.setIsPopular(true);
        bulk.setExpectedCount(1);
        return bulk;
    }

    /**
     * 평점 집계는 rating/ratingCount 만 쓴다 — 큐레이션 폼이 편집하지도, 표시하지도 않는 필드다.
     * 그래서 집계는 엔티티를 거치지 않고 그 두 컬럼만 UPDATE 한다. 시청자 행동이 편집자의 폼을 무효화하면 안 된다.
     *
     * 방어 전(커밋 529cf47)에는 version 이 오르고 폼 저장이 409 로 거절됐다. 그 두 단언이 뒤집힌 것이 이 테스트다.
     */
    @Test
    @DisplayName("사용자 별점은 version 을 올리지 않아 관리자 폼 저장을 막지 않는다")
    void userRatingBumpsVersionAndBlocksAdminSave() {
        AdminAnimeDetailDto seen = service.get(animeId); // 관리자가 폼을 연다

        // 그 사이 사용자가 별점을 바꾼다. 등록/수정/삭제 모두 같은 updateAnimeAggregates 를 탄다.
        // 삭제 경로를 쓰는 이유: 슬라이스에는 Auditing 이 없어 Rating 삽입이 not-null 시각에서 막힌다.
        ratingService.deleteMyRating(userId, animeId);

        assertThat(current().getRating()).isEqualTo(4.0);
        assertThat(current().getVersion()).as("집계 쓰기는 version 을 올리지 않는다").isEqualTo(seen.getVersion());
        assertThat(current().getUpdatedAt()).as("집계 쓰기는 수정 시각도 건드리지 않는다").isEqualTo(seen.getUpdatedAt());

        AnimeCurationUpdateRequest request = new AnimeCurationUpdateRequest();
        request.setIsPopular(true);
        request.setVersion(seen.getVersion());
        AdminAnimeDetailDto saved = service.update(animeId, request);

        assertThat(saved.getIsPopular()).as("별점이 관리자 저장을 막지 않는다").isTrue();
        assertThat(current().getIsPopular()).isTrue();
        assertThat(current().getRating()).as("집계 값은 그대로 남는다").isEqualTo(4.0);
    }
}
