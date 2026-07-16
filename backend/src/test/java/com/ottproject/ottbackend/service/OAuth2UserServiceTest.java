package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.repository.SocialAccountRepository;
import com.ottproject.ottbackend.repository.UserRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

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
 */
@ExtendWith(MockitoExtension.class)
class OAuth2UserServiceTest {

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
}
