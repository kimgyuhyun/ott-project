package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * LocalUserDetailsService 검증
 *
 * 왜 이 테스트가 필요한가
 * - 계정은 항상 소문자로 저장된다(User.normalizeEmail). 로그인 조회가 원본 문자열을 쓰면
 *   대소문자만 다른 입력(TesTer@Example.COM)으로는 저장된 계정을 못 찾아 로그인이 실패한다.
 *   로그인 조회도 저장과 같은 기준으로 정규화하는지 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class LocalUserDetailsServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks
    private LocalUserDetailsService service;

    @Test
    @DisplayName("대소문자/공백이 달라도 저장된(소문자) 계정으로 로그인 조회가 된다")
    void resolvesUserRegardlessOfEmailCasing() {
        User stored = User.createLocalUser("tester@example.com", "{bcrypt}hash", "테스터");
        given(userRepository.findByEmail("tester@example.com")).willReturn(Optional.of(stored));

        UserDetails details = service.loadUserByUsername("  TesTer@Example.COM  ");

        assertThat(details.getUsername()).isEqualTo("tester@example.com");
        assertThat(details.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    @Test
    @DisplayName("존재하지 않는 이메일은 UsernameNotFoundException")
    void unknownEmailThrows() {
        given(userRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("Nobody@Example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
