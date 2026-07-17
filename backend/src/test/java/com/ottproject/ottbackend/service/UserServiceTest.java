package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * UserService 중복 확인 검증
 *
 * 왜 이 테스트가 필요한가
 * - 계정은 항상 소문자로 저장된다(User.createLocalUser 의 trim + toLowerCase, UserFactoryTest 가 고정).
 *   중복 확인이 원본 문자열로 조회하면 저장 규칙과 어긋나, 대소문자만 다른 재가입이 409 를 통과한다.
 *   그 뒤 email 의 unique 제약에 걸려 사용자에겐 500 이 나간다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passWordEncoder;

    @InjectMocks
    private UserService service;

    @Test
    @DisplayName("중복 확인 - 대소문자/공백이 달라도 같은 계정으로 본다(저장 규칙과 같은 기준으로 조회)")
    void existsByEmailNormalizesLikeTheStorageRule() {
        given(userRepository.existsByEmail("tester@example.com")).willReturn(true);

        assertThat(service.existsByEmail("  TesTer@Example.COM  ")).isTrue();

        verify(userRepository).existsByEmail("tester@example.com"); // 원본 그대로 조회하면 계정을 놓친다
    }

    @Test
    @DisplayName("중복 확인 - 이메일이 없으면 조회 없이 false(널 이메일이 500 이 되지 않게)")
    void existsByEmailWithNullIsFalse() {
        assertThat(service.existsByEmail(null)).isFalse();

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("이메일 조회 - 대소문자/공백이 달라도 저장 규칙과 같은 기준으로 조회한다")
    void findByEmailNormalizes() {
        // login 성공 뒤 원본 이메일로 다시 조회하는 경로가 있어, 정규화가 없으면 인증은 통과했는데
        // 사용자 조회에서 못 찾아 401 이 난다.
        User stored = User.createLocalUser("tester@example.com", "{bcrypt}hash", "테스터");
        given(userRepository.findByEmail("tester@example.com")).willReturn(Optional.of(stored));

        assertThat(service.findByEmail("  TesTer@Example.COM  ")).contains(stored);

        verify(userRepository).findByEmail("tester@example.com");
    }
}
