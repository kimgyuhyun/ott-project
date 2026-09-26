package com.ottproject.ottbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ottproject.ottbackend.entity.SocialAccount;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AuthProvider;
import com.ottproject.ottbackend.repository.SocialAccountRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * OAuth2UserService 단위 테스트
 *
 * 지키려는 규칙
 * - 소셜 로그인 사용자의 DB 역할(USER/ADMIN)이 Spring Security 권한(ROLE_*)으로 부여되어야 한다.
 *
 * 회귀 배경(2026-07-16)
 * - DB role 을 attributes 에만 담고 authorities 에는 제공자 기본 권한만 실었다.
 * - 그 결과 DB 가 ADMIN 인 계정도 ROLE_ADMIN 이 없어 /api/admin/** 이 전부 403 이었다.
 *   (프론트는 attributes 의 role 을 보므로 화면만 열리고 API 는 막히는 형태)
 *
 * 결함 기록(2026-09-26)
 * - 같은 이메일의 계정이 있으면 제공자의 검증 플래그만 보고 그 계정에 새 소셜 계정을 연결하고 로그인시킨다.
 *   네이버는 플래그가 없어 코드가 항상 참으로 넘기므로, 네이버 계정 이메일을 남의 이메일로 맞추면 그 사람 계정에 들어간다.
 * - 주인이 같은 제공자 계정을 이미 연결해 뒀으면 검증 플래그조차 보지 않고 주인 계정으로 로그인시킨다.
 * - 아래 "(현재 결함)" 테스트는 이 동작을 기록한다. 수정 커밋이 뒤집는다.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2UserServiceTest {

    private static final String OWNER_EMAIL = "owner@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @InjectMocks
    private OAuth2UserService oAuth2UserService;

    /** 소셜 제공자가 준 원본 OAuth2User (기본 권한만 가짐 — ROLE_* 없음) */
    private OAuth2User providerUser() {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH2_USER"), new SimpleGrantedAuthority("SCOPE_account_email")),
                Map.of("id", 1234L, "email", "admin@example.com"),
                "id");
    }

    private Set<String> authorityNames(OAuth2User user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("DB 역할이 ADMIN 이면 ROLE_ADMIN 권한이 부여된다")
    void grantsRoleAdminWhenDbRoleIsAdmin() {
        User admin = User.createAdminUser("admin@example.com", "pw", "관리자");

        OAuth2User result = oAuth2UserService.createOAuth2User(providerUser(), admin);

        // 핵심: hasRole("ADMIN") 은 authorities 를 보므로 여기에 ROLE_ADMIN 이 있어야 한다
        assertThat(authorityNames(result)).contains("ROLE_ADMIN");
    }

    @Test
    @DisplayName("일반 사용자는 ROLE_USER 만 부여되고 ROLE_ADMIN 은 없다")
    void grantsOnlyRoleUserForNormalUser() {
        User normal = User.createLocalUser("user@example.com", "pw", "일반");

        OAuth2User result = oAuth2UserService.createOAuth2User(providerUser(), normal);

        assertThat(authorityNames(result)).contains("ROLE_USER");
        assertThat(authorityNames(result)).doesNotContain("ROLE_ADMIN"); // 권한 상승 방지
    }

    @Test
    @DisplayName("소셜 제공자의 기본 권한은 그대로 유지된다")
    void keepsProviderAuthorities() {
        User admin = User.createAdminUser("admin@example.com", "pw", "관리자");

        OAuth2User result = oAuth2UserService.createOAuth2User(providerUser(), admin);

        assertThat(authorityNames(result)).contains("OAUTH2_USER", "SCOPE_account_email");
    }

    /**
     * 현재 코드의 동작을 기록한다: 같은 이메일의 계정이 있으면 검증 플래그가 참이라는 것만으로 그 계정에
     * 새 소셜 계정을 연결하고 주인으로 로그인시킨다. 네이버 로그인은 이 플래그가 항상 참이다.
     */
    @Test
    @DisplayName("같은 이메일의 계정이 있으면 제공자가 검증했다고 할 때 그 계정에 새 소셜 계정을 연결하고 로그인시킨다(현재 결함)")
    void attachesNewProviderAccountToExistingEmail() {
        User owner = User.createLocalUser(OWNER_EMAIL, "pw", "주인");
        when(socialAccountRepository.findByProviderAndProviderId(AuthProvider.NAVER, "naver-new"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner));
        when(userRepository.save(owner)).thenReturn(owner);

        User loggedIn = oAuth2UserService.processOAuth2User(OWNER_EMAIL, "다른사람", "naver-new", AuthProvider.NAVER, true);

        // 결함: 이메일이 같다는 것만으로 주인 계정에 모르는 네이버 계정이 붙고, 그 계정으로 로그인된다
        assertThat(loggedIn).isSameAs(owner);
        verify(socialAccountRepository).save(any(SocialAccount.class));
    }

    /**
     * 현재 코드의 동작을 기록한다: 주인이 같은 제공자 계정을 이미 연결해 뒀으면 검증 플래그를 보는 분기를
     * 건너뛰고 주인을 돌려준다.
     */
    @Test
    @DisplayName("주인이 같은 제공자 계정을 이미 연결해 뒀으면, 이메일이 검증되지 않은 다른 계정도 주인 계정으로 로그인시킨다(현재 결함)")
    void logsInAnotherAccountOfTheSameProviderAsOwner() {
        User owner = User.createLocalUser(OWNER_EMAIL, "pw", "주인");
        when(socialAccountRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kakao-other"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner));
        when(socialAccountRepository.existsByUserAndProvider(owner, AuthProvider.KAKAO))
                .thenReturn(true);
        when(userRepository.save(owner)).thenReturn(owner);

        User loggedIn =
                oAuth2UserService.processOAuth2User(OWNER_EMAIL, "다른사람", "kakao-other", AuthProvider.KAKAO, false);

        // 결함: 검증되지 않은 이메일인데도 주인 계정으로 로그인된다
        assertThat(loggedIn).isSameAs(owner);
    }

    @Test
    @DisplayName("처음 보는 이메일이면 새 계정과 소셜 연결을 만든다")
    void createsUserAndSocialLinkForNewEmail() {
        when(socialAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = oAuth2UserService.processOAuth2User(OWNER_EMAIL, "새사용자", "google-1", AuthProvider.GOOGLE, true);

        assertThat(created.getEmail()).isEqualTo(OWNER_EMAIL);
        verify(socialAccountRepository).save(any(SocialAccount.class));
    }
}
