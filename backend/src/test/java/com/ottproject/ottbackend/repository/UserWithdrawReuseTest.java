package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.SocialAccount;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 탈퇴 후 재가입이 실제로 가능한지 검증 (실제 PostgreSQL)
 *
 * 왜 이 테스트가 필요한가
 * - 탈퇴의 목적은 "그 이메일을 놓아주는 것"인데, 그걸 막는 것은 users.email 유니크 제약이다.
 *   제약은 DB 에만 있으므로 목으로는 증명할 수 없다. EmailAuthServiceTest 는 "익명화한 값을
 *   저장하려고 시도한다"까지만 보고, 그 값이 실제로 제약을 통과하는지는 보지 못한다.
 * - 익명화를 되돌리거나(enabled=false 만 남기거나) 익명 이메일 규칙이 충돌하는 값으로 바뀌면
 *   여기서만 잡힌다. 운영에서는 "탈퇴한 사람이 재가입하려는데 이미 가입된 이메일이라고 나온다"로 나타난다.
 * - 소셜 연동도 같다. 행이 남으면 OAuth2UserService 가 (provider, provider_id)로 탈퇴한 계정을
 *   먼저 찾아내므로, 재가입은 새 계정이 아니라 탈퇴 계정 부활이 된다.
 *
 * 왜 H2 로는 안 되는가
 * - 검증 대상이 제약 그 자체다. 운영이 PostgreSQL 이면 PostgreSQL 이 실제로 거절/허용하는지를 봐야 한다.
 *
 * 트랜잭션 구성(명시)
 * - @DataJpaTest 의 기본 트랜잭션을 그대로 쓴다(테스트마다 롤백). 동시성이 아니라 제약만 보므로
 *   커넥션이 하나면 충분하다.
 * - 한 트랜잭션 안에서 익명화(UPDATE)와 재가입(INSERT)을 이어서 하므로 그 사이에 flush 가 필요하다.
 *   하이버네이트는 한 번의 flush 에서 INSERT 를 UPDATE 보다 먼저 내보내기 때문에, 묶어두면
 *   아직 풀리지 않은 주소로 INSERT 가 나가 제약에 걸린다(운영에서는 요청이 달라 생기지 않는 순서다).
 * - 슬라이스에는 Auditing 이 없으므로 not-null 인 SocialAccount.createdAt 은 픽스처가 직접 채운다
 *   (User 는 정적 팩토리가 생성/수정 시각을 직접 채운다).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 컨테이너 URL 을 쓰기 위해 자동 대체를 끈다
@Import(JpaSliceTestSupport.class)
@Testcontainers(disabledWithoutDocker = true)
@Tag("testcontainers") // testFast 가 제외하는 태그. 이 클래스들이 전체 실행 9분 중 8분 40초를 쓴다.
@TestPropertySource(properties = {
        // 엔티티 기준으로 스키마를 만든다. User.email 의 unique 선언이 실제 인덱스가 되는지도 함께 검증된다.
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create", // create-drop 이 아니다: 종료 시 drop DDL 이 이미 내려간 컨테이너에 붙으려다 30초를 버린다
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
})
class UserWithdrawReuseTest {

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

    /** 탈퇴했다가 같은 주소로 돌아오는 사용자의 이메일 */
    private static final String EMAIL = "comeback@example.com";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @BeforeEach
    void setUp() {
        socialAccountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private SocialAccount googleLink(User user, String providerId) {
        SocialAccount link = SocialAccount.createSocialAccount(user, AuthProvider.GOOGLE, providerId, EMAIL);
        ReflectionTestUtils.setField(link, "createdAt", LocalDateTime.now()); // 슬라이스에는 Auditing 이 없다
        return link;
    }

    /**
     * 이 테스트가 이 파일의 존재 이유다. 익명화를 지우거나 약화시키면 여기서 잡힌다.
     */
    @Test
    @DisplayName("탈퇴로 익명화한 뒤에는 같은 이메일로 다시 가입할 수 있다")
    void withdrawnEmailCanBeReused() {
        User withdrawn = userRepository.saveAndFlush(User.createLocalUser(EMAIL, "encoded", "홍길동"));

        withdrawn.withdraw();
        userRepository.saveAndFlush(withdrawn); // 재가입 INSERT 전에 주소를 실제로 놓아준다

        User rejoined = userRepository.saveAndFlush(User.createLocalUser(EMAIL, "encoded2", "홍길동"));

        assertThat(rejoined.getId()).isNotEqualTo(withdrawn.getId()); // 부활이 아니라 새 계정이다
        assertThat(userRepository.findByEmail(EMAIL)).contains(rejoined); // 로그인이 새 계정을 찾는다
        assertThat(userRepository.count()).isEqualTo(2); // 탈퇴 행은 남아 있다(FK 참조 보존)
    }

    /**
     * 위 테스트가 무엇을 증명하는지 고정하는 대조군.
     * 제약이 없으면 익명화 없이도 통과하므로, 위 테스트는 아무것도 검증하지 않는 게 된다.
     */
    @Test
    @DisplayName("익명화하지 않고 비활성화만 하면 같은 이메일 가입이 제약에 걸린다 - 기존 탈퇴가 재가입을 막던 이유")
    void disablingAloneKeepsTheEmailOccupied() {
        User withdrawn = userRepository.saveAndFlush(User.createLocalUser(EMAIL, "encoded", "홍길동"));
        withdrawn.setEnabled(false); // 익명화 이전의 탈퇴 처리
        userRepository.saveAndFlush(withdrawn);

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(User.createLocalUser(EMAIL, "encoded2", "홍길동")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("연동을 지우면 같은 소셜 계정이 탈퇴 계정을 찾지 못하고 새로 연동된다")
    void withdrawnSocialLinkIsReleased() {
        User withdrawn = userRepository.saveAndFlush(User.createLocalUser(EMAIL, "encoded", "홍길동"));
        socialAccountRepository.saveAndFlush(googleLink(withdrawn, "google-123"));

        socialAccountRepository.deleteByUser(withdrawn); // 탈퇴가 하는 일
        withdrawn.withdraw();
        userRepository.saveAndFlush(withdrawn);
        socialAccountRepository.flush();

        // 연동이 남아 있으면 OAuth2UserService 1단계가 여기서 탈퇴 계정을 찾아 그대로 로그인시킨다
        assertThat(socialAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-123")).isEmpty();

        // 같은 구글 계정으로 다시 가입 — (provider, provider_id) 유니크에도 걸리지 않아야 한다
        User rejoined = userRepository.saveAndFlush(User.createLocalUser(EMAIL, "encoded2", "홍길동"));
        socialAccountRepository.saveAndFlush(googleLink(rejoined, "google-123"));

        assertThat(socialAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-123"))
                .get()
                .extracting(link -> link.getUser().getId())
                .isEqualTo(rejoined.getId());
    }
}
