package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.enums.AnimeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 읽기 전용 트랜잭션에서의 단건 조회 동작 검증 (실제 PostgreSQL)
 *
 * 왜 H2 슬라이스로는 안 되는가
 * - AnimeRepository.findById 에는 @Lock(PESSIMISTIC_WRITE) 가 걸려 있어 SELECT ... FOR UPDATE 가 나간다.
 *   PostgreSQL 은 read-only 트랜잭션에서 이를 거부한다:
 *     "cannot execute SELECT FOR UPDATE in a read-only transaction"
 *   H2 는 허용한다. 그래서 큐레이션 단건 조회(@Transactional(readOnly=true))의 500 이
 *   기존 테스트를 전부 통과하고 배포까지 갔다. 운영과 같은 DB 를 띄워서 그 차이를 고정한다.
 *
 * 트랜잭션 구성(명시)
 * - @DataJpaTest 는 각 테스트를 트랜잭션으로 감싸는데, 그건 read-only 가 아니라 재현이 안 된다.
 *   그래서 클래스에 NOT_SUPPORTED 로 그 트랜잭션을 끄고, TransactionTemplate 으로
 *   readOnly 트랜잭션을 직접 열어 그 안에서 조회한다.
 * - 트랜잭션 롤백이 없으므로 픽스처는 각 테스트가 직접 정리한다.
 *
 * Docker 가 없으면 컨테이너를 못 띄운다. Testcontainers 가 그 경우 조건부로 테스트를 건너뛴다
 * (@Testcontainers 의 disabledWithoutDocker).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 컨테이너 URL 을 쓰기 위해 자동 대체를 끈다
@Import(JpaSliceTestSupport.class)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        // Flyway 마이그레이션 대신 엔티티 기준 스키마를 만든다(이 테스트는 스키마 이력이 아니라 락 동작을 본다).
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED) // @DataJpaTest 의 감싸는 트랜잭션을 끈다
class AnimeRepositoryReadOnlyLockTest {

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
    private AnimeRepository animeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long animeId;

    @BeforeEach
    void setUp() {
        animeRepository.deleteAll();
        Anime anime = new Anime();
        anime.setTitle("작품");
        anime.setStatus(AnimeStatus.ONGOING);
        anime.setYear(2026);
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
        anime.setCreatedAt(LocalDateTime.now());
        anime.setUpdatedAt(LocalDateTime.now());
        animeId = animeRepository.save(anime).getId();
    }

    private TransactionTemplate readOnlyTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }

    /**
     * 이 테스트가 이 파일의 존재 이유다. 조회 경로를 findById 로 되돌리면 여기서 잡힌다.
     */
    // Hibernate 의 PostgreSQL 방언은 PESSIMISTIC_WRITE 를 "for no key update" 로 낸다(psql 로 재현할 때의
    // "FOR UPDATE" 와 문구가 다르다). 거부 사유는 같으므로 예외 타입이 아니라 메시지로 단정한다.
    @Test
    @DisplayName("읽기 전용 트랜잭션에서 락 걸린 findById 는 PostgreSQL 이 거부한다 - 500 의 원인")
    void lockedFindByIdFailsInsideReadOnlyTransaction() {
        assertThatThrownBy(() -> readOnlyTransaction().execute(status -> animeRepository.findById(animeId)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("read-only transaction");
    }

    @Test
    @DisplayName("읽기 전용 트랜잭션에서 락 없는 조회는 정상 동작한다 - 큐레이션 단건 조회가 쓰는 경로")
    void lockFreeFindSucceedsInsideReadOnlyTransaction() {
        Optional<Anime> found = readOnlyTransaction()
                .execute(status -> animeRepository.findByIdWithoutLock(animeId));

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("작품");
    }

    @Test
    @DisplayName("쓰기 트랜잭션에서는 락 걸린 findById 가 정상 동작한다 - update 경로의 동시 수정 방어는 유지된다")
    void lockedFindByIdStillWorksInsideWriteTransaction() {
        TransactionTemplate writeTransaction = new TransactionTemplate(transactionManager);

        assertThatCode(() -> writeTransaction.execute(status -> animeRepository.findById(animeId)))
                .doesNotThrowAnyException();
    }
}
