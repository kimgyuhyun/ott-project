package com.ottproject.ottbackend.repository.curation;

import com.ottproject.ottbackend.config.QuerydslConfig;
import com.ottproject.ottbackend.dto.admin.AnimeCurationSearchCondition;
import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.enums.SyncOrigin;
import com.ottproject.ottbackend.repository.JpaSliceTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 큐레이션 검색(QueryDSL)의 동적 조건 조합 검증
 *
 * 왜 이 레벨인가
 * - 조건 9개가 자유 조합되므로 조합 수가 수백이다. 조립한 술어가 실제 SQL 로 옳게 번역되는지는
 *   DB 에 붙여야만 알 수 있다. 서비스에서 목으로 덮으면 술어가 틀려도 전부 통과한다
 *   (같은 방식으로 limit 빠진 JPQL 이 실장애가 날 때까지 살아남은 적이 있다).
 *
 * 슬라이스 구성(명시)
 * - JpaSliceTestSupport: 메인 클래스의 @MapperScan 때문에 필요한 SqlSessionFactory 스텁
 * - QuerydslConfig: JPAQueryFactory 빈
 * - AnimeCurationQueryRepository: @DataJpaTest 는 Spring Data 리포지토리만 등록하고
 *   일반 @Repository 컴포넌트는 스캔에서 제외하므로 직접 임포트해야 한다.
 * - JpaAuditingConfig 는 싣지 않는다 — 픽스처가 createdAt/updatedAt 을 직접 넣고,
 *   Auditing 이 끼면 저장 시각으로 덮어써서 시각 기반 검증이 불가능해진다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 아래 URL 을 쓰기 위해 자동 대체를 끈다
@Import({JpaSliceTestSupport.class, QuerydslConfig.class, AnimeCurationQueryRepository.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // application.yml 이 dev 프로파일을 활성화하고 application-dev.yml 이 PostgreSQLDialect 를 지정한다.
        // 그대로 두면 H2 를 상대로 PostgreSQL 전용 SQL(set client_min_messages 등)을 쏴서 스키마 생성이 깨진다.
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        // H2 2.x 는 YEAR 를 예약어로 취급한다(PostgreSQL 은 아니다). Anime.year 컬럼 때문에
        // create table anime 이 통째로 실패하는데, Hibernate 는 DDL 오류를 삼키므로 조회 시점에야
        // "Table ANIME not found" 로 드러난다. 예약어에서 빼서 운영(PostgreSQL)과 같은 DDL 이 통하게 한다.
        "spring.datasource.url=jdbc:h2:mem:anime_curation;DB_CLOSE_DELAY=-1;NON_KEYWORDS=YEAR",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // DDL 오류를 조용히 넘기지 않는다 — 위 두 문제가 그렇게 숨어 있었다.
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
})
class AnimeCurationQueryRepositoryTest {

    @Autowired
    private AnimeCurationQueryRepository curationQueryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 17, 12, 0);

    /**
     * Anime.createAnime 은 인자가 32개라 픽스처로 쓰기 어렵다.
     * not-null 컬럼만 채운 최소 엔티티를 만들고, 각 테스트가 필요한 필드만 덮어쓴다.
     */
    private Anime anime(String title, AnimeStatus status, Integer year) {
        Anime anime = new Anime();
        anime.setTitle(title);
        anime.setStatus(status);
        anime.setYear(year);
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
        anime.setCreatedAt(NOW);
        anime.setUpdatedAt(NOW);
        return anime;
    }

    private Anime persist(Anime anime) {
        return entityManager.persist(anime);
    }

    private List<Long> searchIds(AnimeCurationSearchCondition condition) {
        return curationQueryRepository.search(condition, 0, 50).stream().map(Anime::getId).toList();
    }

    private AnimeCurationSearchCondition emptyCondition() {
        return new AnimeCurationSearchCondition();
    }

    @Nested
    @DisplayName("조건 없음")
    class NoCondition {

        @Test
        @DisplayName("조건이 비면 전체를 돌려준다 - 검색에서는 정상이다")
        void returnsEverything() {
            persist(anime("A", AnimeStatus.ONGOING, 2026));
            persist(anime("B", AnimeStatus.COMPLETED, 2025));

            assertThat(searchIds(emptyCondition())).hasSize(2);
            assertThat(curationQueryRepository.countByCondition(emptyCondition())).isEqualTo(2);
        }

        @Test
        @DisplayName("공백뿐인 제목 키워드는 조건으로 치지 않는다")
        void blankKeywordIsNotACondition() {
            persist(anime("A", AnimeStatus.ONGOING, 2026));
            AnimeCurationSearchCondition condition = emptyCondition();
            condition.setTitleKeyword("   ");

            assertThat(searchIds(condition)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("제목 키워드 - 한/영/일 통합")
    class TitleKeyword {

        @Test
        @DisplayName("한국어 제목에 걸린다")
        void matchesKoreanTitle() {
            Anime target = persist(anime("귀멸의 칼날", AnimeStatus.COMPLETED, 2019));
            persist(anime("나루토", AnimeStatus.COMPLETED, 2002));

            AnimeCurationSearchCondition condition = emptyCondition();
            condition.setTitleKeyword("귀멸");

            assertThat(searchIds(condition)).containsExactly(target.getId());
        }

        @Test
        @DisplayName("영어 제목에만 있어도 걸린다 - 한국어 제목이 아직 없을 수 있다")
        void matchesEnglishTitle() {
            Anime target = anime(null, AnimeStatus.COMPLETED, 2019);
            target.setTitleEn("Demon Slayer");
            persist(target);
            persist(anime("나루토", AnimeStatus.COMPLETED, 2002));

            AnimeCurationSearchCondition condition = emptyCondition();
            condition.setTitleKeyword("Demon");

            assertThat(searchIds(condition)).containsExactly(target.getId());
        }

        @Test
        @DisplayName("일본어 제목에만 있어도 걸린다")
        void matchesJapaneseTitle() {
            Anime target = anime(null, AnimeStatus.COMPLETED, 2019);
            target.setTitleJp("鬼滅の刃");
            persist(target);

            AnimeCurationSearchCondition condition = emptyCondition();
            condition.setTitleKeyword("鬼滅");

            assertThat(searchIds(condition)).containsExactly(target.getId());
        }

        @Test
        @DisplayName("대소문자를 가리지 않는다")
        void ignoresCase() {
            Anime target = anime(null, AnimeStatus.COMPLETED, 2019);
            target.setTitleEn("Demon Slayer");
            persist(target);

            AnimeCurationSearchCondition condition = emptyCondition();
            condition.setTitleKeyword("demon slayer");

            assertThat(searchIds(condition)).containsExactly(target.getId());
        }

        @Test
        @DisplayName("앞뒤 공백은 무시하고 검색한다")
        void trimsKeyword() {
            Anime target = persist(anime("귀멸의 칼날", AnimeStatus.COMPLETED, 2019));

            AnimeCurationSearchCondition condition = emptyCondition();
            condition.setTitleKeyword("  귀멸  ");

            assertThat(searchIds(condition)).containsExactly(target.getId());
        }
    }

    @Nested
    @DisplayName("단일 조건")
    class SingleCondition {

        @Test
        @DisplayName("상태로 거른다")
        void filtersByStatus() {
            Anime ongoing = persist(anime("A", AnimeStatus.ONGOING, 2026));
            persist(anime("B", AnimeStatus.COMPLETED, 2026));

            AnimeCurationSearchCondition condition = emptyCondition();
            condition.setStatus(AnimeStatus.ONGOING);

            assertThat(searchIds(condition)).containsExactly(ongoing.getId());
        }

        @Test
        @DisplayName("연도로 거른다")
        void filtersByYear() {
            Anime y2026 = persist(anime("A", AnimeStatus.ONGOING, 2026));
            persist(anime("B", AnimeStatus.ONGOING, 2025));

            AnimeCurationSearchCondition condition = emptyCondition();
            condition.setYear(2026);

            assertThat(searchIds(condition)).containsExactly(y2026.getId());
        }

        @Test
        @DisplayName("비활성 작품만 뽑는다 - 사용자에게 안 보이는 작품을 찾는 경로")
        void filtersByInactive() {
            Anime inactive = anime("A", AnimeStatus.ONGOING, 2026);
            inactive.setIsActive(false);
            persist(inactive);
            persist(anime("B", AnimeStatus.ONGOING, 2026));

            AnimeCurationSearchCondition condition = emptyCondition();
            condition.setIsActive(false);

            assertThat(searchIds(condition)).containsExactly(inactive.getId());
        }

        @Test
        @DisplayName("배지로 거른다")
        void filtersByBadges() {
            Anime exclusive = anime("A", AnimeStatus.ONGOING, 2026);
            exclusive.setIsExclusive(true);
            persist(exclusive);
            Anime popular = anime("B", AnimeStatus.ONGOING, 2026);
            popular.setIsPopular(true);
            persist(popular);

            AnimeCurationSearchCondition byExclusive = emptyCondition();
            byExclusive.setIsExclusive(true);
            assertThat(searchIds(byExclusive)).containsExactly(exclusive.getId());

            AnimeCurationSearchCondition byPopular = emptyCondition();
            byPopular.setIsPopular(true);
            assertThat(searchIds(byPopular)).containsExactly(popular.getId());
        }

        @Test
        @DisplayName("큐레이션 여부로 거른다")
        void filtersByCurated() {
            Anime curated = anime("A", AnimeStatus.ONGOING, 2026);
            curated.setCurated(true);
            persist(curated);
            persist(anime("B", AnimeStatus.ONGOING, 2026));

            AnimeCurationSearchCondition condition = emptyCondition();
            condition.setCurated(true);

            assertThat(searchIds(condition)).containsExactly(curated.getId());
        }
    }

    @Nested
    @DisplayName("유입 경로 - malId 유무에서 파생")
    class SyncOriginFilter {

        @Test
        @DisplayName("JIKAN 은 malId 가 있는 작품만 잡는다")
        void jikanMatchesRowsWithMalId() {
            Anime synced = anime("A", AnimeStatus.ONGOING, 2026);
            synced.setMalId(1234L);
            persist(synced);
            persist(anime("B", AnimeStatus.ONGOING, 2026)); // malId 없음

            AnimeCurationSearchCondition condition = emptyCondition();
            condition.setSyncOrigin(SyncOrigin.JIKAN);

            assertThat(searchIds(condition)).containsExactly(synced.getId());
        }

        @Test
        @DisplayName("MANUAL 은 malId 가 없는 작품만 잡는다")
        void manualMatchesRowsWithoutMalId() {
            Anime synced = anime("A", AnimeStatus.ONGOING, 2026);
            synced.setMalId(1234L);
            persist(synced);
            Anime manual = persist(anime("B", AnimeStatus.ONGOING, 2026));

            AnimeCurationSearchCondition condition = emptyCondition();
            condition.setSyncOrigin(SyncOrigin.MANUAL);

            assertThat(searchIds(condition)).containsExactly(manual.getId());
        }
    }

    @Nested
    @DisplayName("조건 조합")
    class CombinedConditions {

        @Test
        @DisplayName("연도와 배지를 함께 걸면 둘 다 맞는 것만 남는다 - AND 로 묶인다")
        void combinesYearAndBadge() {
            Anime match = anime("A", AnimeStatus.ONGOING, 2026);
            match.setIsPopular(true);
            persist(match);

            Anime wrongYear = anime("B", AnimeStatus.ONGOING, 2025);
            wrongYear.setIsPopular(true);
            persist(wrongYear);

            persist(anime("C", AnimeStatus.ONGOING, 2026)); // 배지 없음

            AnimeCurationSearchCondition condition = emptyCondition();
            condition.setYear(2026);
            condition.setIsPopular(true);

            assertThat(searchIds(condition)).containsExactly(match.getId());
        }

        @Test
        @DisplayName("상태와 노출 여부를 함께 건다")
        void combinesStatusAndActive() {
            Anime match = anime("A", AnimeStatus.ONGOING, 2026);
            match.setIsActive(false);
            persist(match);
            persist(anime("B", AnimeStatus.ONGOING, 2026));            // 활성
            Anime completedInactive = anime("C", AnimeStatus.COMPLETED, 2026);
            completedInactive.setIsActive(false);
            persist(completedInactive);

            AnimeCurationSearchCondition condition = emptyCondition();
            condition.setStatus(AnimeStatus.ONGOING);
            condition.setIsActive(false);

            assertThat(searchIds(condition)).containsExactly(match.getId());
        }

        @Test
        @DisplayName("제목 키워드와 다른 조건을 함께 건다 - 키워드 내부 OR 가 AND 를 삼키면 안 된다")
        void combinesKeywordWithOtherCondition() {
            Anime match = anime("귀멸의 칼날", AnimeStatus.ONGOING, 2026);
            persist(match);
            persist(anime("귀멸의 칼날 2기", AnimeStatus.COMPLETED, 2026)); // 키워드는 맞지만 상태가 다르다

            AnimeCurationSearchCondition condition = emptyCondition();
            condition.setTitleKeyword("귀멸");
            condition.setStatus(AnimeStatus.ONGOING);

            assertThat(searchIds(condition)).containsExactly(match.getId());
        }

        @Test
        @DisplayName("어느 조건에도 안 맞으면 빈 결과다")
        void returnsEmptyWhenNothingMatches() {
            persist(anime("A", AnimeStatus.ONGOING, 2026));

            AnimeCurationSearchCondition condition = emptyCondition();
            condition.setYear(1999);

            assertThat(searchIds(condition)).isEmpty();
            assertThat(curationQueryRepository.countByCondition(condition)).isZero();
        }
    }

    @Nested
    @DisplayName("페이지네이션")
    class Pagination {

        @Test
        @DisplayName("페이지 크기만큼 끊어서 돌려주고 전체 건수는 조건 전체를 센다")
        void paginatesWhileCountingAll() {
            for (int i = 0; i < 5; i++) {
                persist(anime("A" + i, AnimeStatus.ONGOING, 2026));
            }

            List<Anime> firstPage = curationQueryRepository.search(emptyCondition(), 0, 2);
            List<Anime> secondPage = curationQueryRepository.search(emptyCondition(), 1, 2);

            assertThat(firstPage).hasSize(2);
            assertThat(secondPage).hasSize(2);
            assertThat(firstPage).doesNotContainAnyElementsOf(secondPage); // 페이지가 겹치지 않는다
            assertThat(curationQueryRepository.countByCondition(emptyCondition())).isEqualTo(5);
        }

        @Test
        @DisplayName("마지막 페이지는 남은 만큼만 준다")
        void lastPageReturnsRemainder() {
            for (int i = 0; i < 5; i++) {
                persist(anime("A" + i, AnimeStatus.ONGOING, 2026));
            }

            assertThat(curationQueryRepository.search(emptyCondition(), 2, 2)).hasSize(1);
        }

        @Test
        @DisplayName("범위를 넘긴 페이지는 빈 결과다")
        void pageBeyondRangeIsEmpty() {
            persist(anime("A", AnimeStatus.ONGOING, 2026));

            assertThat(curationQueryRepository.search(emptyCondition(), 10, 20)).isEmpty();
        }

        @Test
        @DisplayName("최신 등록순으로 정렬된다")
        void ordersByIdDesc() {
            Anime first = persist(anime("A", AnimeStatus.ONGOING, 2026));
            Anime second = persist(anime("B", AnimeStatus.ONGOING, 2026));

            assertThat(searchIds(emptyCondition())).containsExactly(second.getId(), first.getId());
        }
    }
}
