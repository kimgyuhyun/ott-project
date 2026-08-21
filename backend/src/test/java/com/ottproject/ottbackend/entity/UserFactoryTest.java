package com.ottproject.ottbackend.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottproject.ottbackend.enums.AuthProvider;
import com.ottproject.ottbackend.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * User 정적 팩토리 검증
 *
 * 왜 이 테스트가 필요한가
 * - 이메일 정규화(trim + 소문자)는 게터가 아니라 계정 동일성 규칙이다.
 *   인증 티켓 키 정규화(fb3c28e)가 "계정은 항상 소문자로 저장된다" 는 이 동작에 의존한다.
 * - 팩토리의 거부 분기(null/blank)와 보안 기본값(권한/인증여부)은 서비스 테스트가 정상 입력만
 *   넘기느라 한 번도 밟지 않는다.
 */
class UserFactoryTest {

    @Nested
    @DisplayName("createLocalUser")
    class CreateLocalUser {

        @Test
        @DisplayName("이메일을 trim 하고 소문자로 정규화해 저장한다 - 계정 동일성 기준")
        void normalizesEmail() {
            User user = User.createLocalUser("  TesTer@Example.COM  ", "password", "테스터");

            assertThat(user.getEmail()).isEqualTo("tester@example.com");
        }

        @Test
        @DisplayName("이름의 앞뒤 공백을 제거한다")
        void trimsName() {
            User user = User.createLocalUser("a@example.com", "password", "  테스터  ");

            assertThat(user.getName()).isEqualTo("테스터");
        }

        @Test
        @DisplayName("비밀번호는 정규화하지 않는다 - 인코딩된 값이 훼손되면 안 된다")
        void keepsPasswordAsIs() {
            User user = User.createLocalUser("a@example.com", "  {bcrypt}hash  ", "테스터");

            assertThat(user.getPassword()).isEqualTo("  {bcrypt}hash  ");
        }

        @Test
        @DisplayName("가입 직후에는 이메일 미인증 상태이며 일반 권한의 로컬 계정이다")
        void appliesSecurityDefaults() {
            User user = User.createLocalUser("a@example.com", "password", "테스터");

            assertThat(user.getRole()).isEqualTo(UserRole.USER);
            assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
            assertThat(user.isEmailVerified()).isFalse();
            assertThat(user.isEnabled()).isTrue();
            assertThat(user.getProviderId()).isNull();
        }

        @Test
        @DisplayName("이메일이 없거나 공백뿐이면 거부한다")
        void rejectsMissingEmail() {
            assertThatThrownBy(() -> User.createLocalUser(null, "password", "테스터"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> User.createLocalUser("   ", "password", "테스터"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("비밀번호가 없거나 공백뿐이면 거부한다 - 로컬 계정은 비밀번호가 필수다")
        void rejectsMissingPassword() {
            assertThatThrownBy(() -> User.createLocalUser("a@example.com", null, "테스터"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> User.createLocalUser("a@example.com", "   ", "테스터"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("이름이 없거나 공백뿐이면 거부한다")
        void rejectsMissingName() {
            assertThatThrownBy(() -> User.createLocalUser("a@example.com", "password", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> User.createLocalUser("a@example.com", "password", "   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("createAdminUser")
    class CreateAdminUser {

        @Test
        @DisplayName("관리자 권한과 인증 완료 상태로 생성된다")
        void createsAdminWithElevatedRole() {
            User admin = User.createAdminUser("Admin@Example.com", "password", "관리자");

            assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
            assertThat(admin.isEmailVerified()).isTrue();
            assertThat(admin.getEmail()).isEqualTo("admin@example.com");
        }

        @Test
        @DisplayName("비밀번호 없이는 관리자를 만들 수 없다")
        void rejectsMissingPassword() {
            assertThatThrownBy(() -> User.createAdminUser("admin@example.com", null, "관리자"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("createSocialUser")
    class CreateSocialUser {

        @Test
        @DisplayName("소셜 계정은 비밀번호 없이 생성되고 이메일 인증 완료로 간주된다")
        void createsSocialUserWithoutPassword() {
            User user =
                    User.createSocialUser("Social@Example.com", "소셜", AuthProvider.GOOGLE, "google-123", "http://img");

            assertThat(user.getPassword()).isNull();
            assertThat(user.isEmailVerified()).isTrue();
            assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
            assertThat(user.getEmail()).isEqualTo("social@example.com");
            assertThat(user.getRole()).isEqualTo(UserRole.USER);
        }

        @Test
        @DisplayName("프로필 이미지는 선택이라 없어도 생성된다")
        void allowsNullProfileImage() {
            User user = User.createSocialUser("social@example.com", "소셜", AuthProvider.GOOGLE, "google-123", null);

            assertThat(user.getProfileImage()).isNull();
        }

        @Test
        @DisplayName("제공자와 제공자 ID 가 없으면 거부한다 - 소셜 계정 식별 불가")
        void rejectsMissingProviderInfo() {
            assertThatThrownBy(() -> User.createSocialUser("social@example.com", "소셜", null, "google-123", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> User.createSocialUser("social@example.com", "소셜", AuthProvider.GOOGLE, "  ", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("withdraw")
    class Withdraw {

        private User savedUser(long id) {
            User user = User.createLocalUser("user@example.com", "encoded-pw", "홍길동");
            user.setId(id); // 익명 이메일이 PK 를 쓰므로 저장된 상태를 흉내낸다
            return user;
        }

        @Test
        @DisplayName("이메일을 예약 TLD(.invalid) 주소로 바꾼다 - 같은 주소로 재가입할 수 있어야 한다")
        void anonymizesEmailSoTheAddressIsReleased() {
            User user = savedUser(7L);

            user.withdraw();

            // 원래 주소가 남아 있으면 유니크 제약이 그 주소를 영구 점유해 재가입이 막힌다
            assertThat(user.getEmail()).isNotEqualTo("user@example.com");
            // .invalid 는 실제 주소로 존재할 수 없어(RFC 2606) 재가입 이메일과 충돌하지 않는다
            assertThat(user.getEmail()).isEqualTo("withdrawn-7@withdrawn.invalid");
        }

        @Test
        @DisplayName("탈퇴 계정끼리도 이메일이 겹치지 않는다 - PK 로 구분한다")
        void anonymizedEmailsDoNotCollideBetweenUsers() {
            User first = savedUser(7L);
            User second = savedUser(8L);

            first.withdraw();
            second.withdraw();

            assertThat(first.getEmail()).isNotEqualTo(second.getEmail());
        }

        @Test
        @DisplayName("식별 정보를 지우고 계정을 비활성화한다")
        void clearsIdentifyingFields() {
            User user = savedUser(7L);
            user.setProviderId("google-123");
            user.setProfileImage("http://img");
            user.setEmailVerified(true);

            user.withdraw();

            assertThat(user.getName()).isEqualTo("탈퇴한 사용자"); // 댓글/리뷰에 실명이 남지 않는다
            assertThat(user.getPassword()).isNull(); // 자격증명은 파기한다
            // providerId 가 남으면 소셜 재로그인이 이 계정을 다시 붙잡아 탈퇴가 무효가 된다
            assertThat(user.getProviderId()).isNull();
            assertThat(user.getProfileImage()).isNull();
            assertThat(user.isEmailVerified()).isFalse();
            assertThat(user.isEnabled()).isFalse(); // 로그인 차단
        }

        @Test
        @DisplayName("저장 전 사용자는 탈퇴할 수 없다 - PK 없이는 충돌하지 않는 이메일을 만들 수 없다")
        void rejectsUnsavedUser() {
            User user = User.createLocalUser("user@example.com", "encoded-pw", "홍길동");

            assertThatThrownBy(user::withdraw).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("normalizeEmail")
    class NormalizeEmail {

        @Test
        @DisplayName("trim + 소문자로 정규화한다 - 계정을 찾는 모든 경로가 저장 규칙과 같은 기준을 쓴다")
        void trimsAndLowercases() {
            assertThat(User.normalizeEmail("  TesTer@Example.COM  ")).isEqualTo("tester@example.com");
        }

        @Test
        @DisplayName("null 은 null 로 둔다 - 이메일 없는 조회가 NPE 로 터지지 않게")
        void keepsNull() {
            assertThat(User.normalizeEmail(null)).isNull();
        }
    }
}
