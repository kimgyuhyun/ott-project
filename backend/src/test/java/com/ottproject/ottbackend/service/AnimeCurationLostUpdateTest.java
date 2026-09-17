package com.ottproject.ottbackend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottproject.ottbackend.dto.admin.AdminAnimeDetailDto;
import com.ottproject.ottbackend.dto.admin.AnimeCurationUpdateRequest;
import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.entity.EntityTestFixtures;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.JpaSliceTestSupport;
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
 * 관리자 단건 큐레이션 수정의 갱신 분실 재현 (실제 PostgreSQL, 실제 커밋)
 *
 * 결함
 * - update 는 findById 의 PESSIMISTIC_WRITE 로 행을 잠그지만, 그 락은 트랜잭션 하나 동안만 유지된다.
 *   관리자가 수정 폼을 연 조회 요청과 저장 요청은 서로 다른 트랜잭션이라 그 사이를 지켜주지 못한다.
 * - 흐름: A 가 폼을 연다 → B 가 같은 작품을 읽고 제목을 고쳐 저장 → A 가 옛 화면 기준으로 제목을 저장
 *   → B 의 제목이 조용히 사라진다.
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
@Import({JpaSliceTestSupport.class, AnimeCurationService.class})
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

    @MockitoBean
    private AnimeCurationQueryRepository curationQueryRepository; // 단건 수정 경로는 쓰지 않는다

    @MockitoBean
    private AnimeCacheService animeCacheService; // Redis 캐시 무효화. 이 결함과 무관하다

    private Long animeId;

    @BeforeEach
    void setUp() {
        animeRepository.deleteAll();
        animeId = animeRepository.save(anime("원래 제목")).getId();
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
        anime.setCurated(false);
        anime.setCurrentEpisodes(0);
        anime.setCreatedAt(now); // 슬라이스에는 Auditing 이 없어 직접 채운다
        anime.setUpdatedAt(now);
        return anime;
    }

    private AnimeCurationUpdateRequest titleChange(String title) {
        AnimeCurationUpdateRequest request = new AnimeCurationUpdateRequest();
        request.setTitle(title);
        return request;
    }

    /**
     * 현재 코드의 동작을 기록한다: 갱신 분실이 일어난다(이 테스트는 초록).
     *
     * 방어가 들어가면 뒤집혀야 하는 단언
     * - adminASave 가 성공한다 → 거절(409)돼야 한다
     * - 최종 제목이 "A 제목" 이다 → "B 제목" 이 남아야 한다
     */
    @Test
    @DisplayName("두 관리자가 같은 시점 상태로 제목을 고치면 나중 저장이 먼저 저장을 덮어쓴다(현재 결함)")
    void laterSaveOverwritesEarlierSave() {
        // 1) 두 관리자가 같은 시점의 상태로 수정 폼을 연다(조회 요청은 여기서 끝난다)
        AdminAnimeDetailDto seenByA = service.get(animeId);
        AdminAnimeDetailDto seenByB = service.get(animeId);
        assertThat(seenByA.getTitle()).isEqualTo(seenByB.getTitle()).isEqualTo("원래 제목");

        // 2) B 가 먼저 저장해 커밋한다
        service.update(animeId, titleChange("B 제목"));
        assertThat(animeRepository.findByIdWithoutLock(animeId).orElseThrow().getTitle())
                .isEqualTo("B 제목");

        // 3) A 가 옛 화면("원래 제목") 기준으로 저장한다 — B 의 수정을 본 적이 없다
        AdminAnimeDetailDto adminASave = service.update(animeId, titleChange("A 제목"));

        // 결함: 거절되지 않고, B 의 제목이 사라진다
        assertThat(adminASave.getTitle()).isEqualTo("A 제목");
        assertThat(animeRepository.findByIdWithoutLock(animeId).orElseThrow().getTitle())
                .as("B 의 수정이 사라졌다")
                .isEqualTo("A 제목");
    }
}
