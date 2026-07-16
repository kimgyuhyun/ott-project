package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.AuthRegisterRequestDto;
import com.ottproject.ottbackend.dto.UserResponseDto;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AuthProvider;
import com.ottproject.ottbackend.enums.UserRole;
import com.ottproject.ottbackend.mappers.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * EmailAuthService 단위 테스트
 *
 * 지키려는 규칙(이메일 인증 흐름)
 * - 가입은 이메일 중복을 막는다(계정 탈취/혼선 방지)
 * - 로그인 실패 사유는 계정 존재 여부를 노출하지 않는다(계정 열거 공격 방어)
 * - 비활성(탈퇴) 계정은 401 이 아니라 403 으로 구분한다
 * - 비밀번호 변경은 현재 비밀번호를 반드시 확인한다(세션 탈취 시 계정 영구 탈취 방지)
 *
 * 경계 메모
 * - 비밀번호 암호화는 UserService.saveUser 안에서 일어난다. 여기서는 userService 가 목이라
 *   "평문을 그대로 넘긴다"까지만 검증한다(암호화 자체는 UserService 의 책임).
 * - login 은 인증을 AuthenticationManager 에 위임하므로, 소셜 가입자 차단·비번 대조 같은
 *   판단은 LocalUserDetailsService/DaoAuthenticationProvider 몫이다. 이 테스트는 위임 결과
 *   (예외 종류)를 어떤 HTTP 상태로 번역하는지만 본다.
 */
@ExtendWith(MockitoExtension.class)
class EmailAuthServiceTest {

    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private EmailAuthService service;

    private User localUser() {
        User user = User.createLocalUser("user@test.com", "encoded-pw", "홍길동");
        user.setId(1L);
        return user;
    }

    private AuthRegisterRequestDto registerReq() {
        AuthRegisterRequestDto req = new AuthRegisterRequestDto();
        req.setEmail("user@test.com");
        req.setPassword("raw-pw");
        req.setName("홍길동");
        return req;
    }

    // ===== 회원가입 =====

    @Test
    @DisplayName("가입 - 이미 가입된 이메일이면 409, 저장을 시도조차 하지 않는다")
    void registerWithDuplicateEmailIsRejected() {
        given(userService.existsByEmail("user@test.com")).willReturn(true);

        assertThatThrownBy(() -> service.register(registerReq()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이미 가입된 이메일입니다");
        verify(userService, never()).saveUser(any());
    }

    @Test
    @DisplayName("가입 - 신규 이메일이면 LOCAL/USER 권한 사용자로 저장한다")
    void registerCreatesLocalUser() {
        given(userService.existsByEmail("user@test.com")).willReturn(false);
        given(userService.saveUser(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        service.register(registerReq());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).saveUser(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("user@test.com");
        assertThat(saved.getName()).isEqualTo("홍길동");
        // 이메일 가입자는 소셜 경로로 오인되면 비번 검증이 통째로 우회된다(LocalUserDetailsService 참고)
        assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        // 가입 요청으로 관리자가 만들어지면 안 된다
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        // 암호화는 UserService.saveUser 의 책임 → 여기서는 평문이 그대로 넘어가는 게 정상
        assertThat(saved.getPassword()).isEqualTo("raw-pw");
    }

    // ===== 로그인 =====

    @Test
    @DisplayName("로그인 - 비활성(탈퇴) 계정은 403")
    void loginWithDisabledAccountIsForbidden() {
        given(authenticationManager.authenticate(any())).willThrow(new DisabledException("disabled"));

        assertThatThrownBy(() -> service.login("user@test.com", "raw-pw"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("비활성화된 계정입니다")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("로그인 - 비밀번호 불일치는 401이고, 계정 존재 여부를 노출하지 않는다")
    void loginWithWrongPasswordIsUnauthorized() {
        given(authenticationManager.authenticate(any())).willThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> service.login("user@test.com", "wrong-pw"))
                .isInstanceOf(ResponseStatusException.class)
                // "비밀번호가 틀렸다"가 아니라 둘 중 무엇이 틀렸는지 알 수 없는 문구여야 한다
                .hasMessageContaining("이메일 또는 비밀번호가 올바르지 않습니다")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("로그인 - 없는 이메일도 비밀번호 불일치와 같은 401 메시지를 쓴다(계정 열거 방어)")
    void loginWithUnknownEmailGivesSameMessageAsWrongPassword() {
        given(authenticationManager.authenticate(any())).willThrow(new BadCredentialsException("no user"));

        assertThatThrownBy(() -> service.login("nobody@test.com", "raw-pw"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이메일 또는 비밀번호가 올바르지 않습니다");
    }

    @Test
    @DisplayName("로그인 성공 - 인증 통과 후 사용자 정보를 DTO 로 반환한다")
    void loginSucceeds() {
        User user = localUser();
        UserResponseDto dto = new UserResponseDto();
        given(userService.findByEmail("user@test.com")).willReturn(Optional.of(user));
        given(userMapper.toUserResponseDto(user)).willReturn(dto);

        assertThat(service.login("user@test.com", "raw-pw")).isSameAs(dto);
        verify(authenticationManager).authenticate(any());
    }

    @Test
    @DisplayName("로그인 - 인증은 통과했는데 사용자 조회가 비면 401 (인증/조회 경로 불일치 방어)")
    void loginWithMissingUserAfterAuthIsUnauthorized() {
        given(userService.findByEmail("user@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("user@test.com", "raw-pw"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ===== 비밀번호 변경 =====

    @Test
    @DisplayName("비밀번호 변경 - 현재 비밀번호가 틀리면 거부하고 저장하지 않는다")
    void changePasswordWithWrongCurrentPasswordIsRejected() {
        User user = localUser();
        given(userService.findByEmail("user@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-pw", "encoded-pw")).willReturn(false);

        assertThatThrownBy(() -> service.changePassword("user@test.com", "wrong-pw", "new-pw"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("현재 비밀번호가 올바르지 않습니다");
        // 핵심: 세션만 탈취한 공격자가 현재 비번 없이 계정을 영구 탈취하지 못해야 한다
        verify(userService, never()).saveUser(any());
        assertThat(user.getPassword()).isEqualTo("encoded-pw");
    }

    @Test
    @DisplayName("비밀번호 변경 - 현재 비밀번호가 맞으면 새 비밀번호를 암호화해 저장한다")
    void changePasswordEncodesNewPassword() {
        User user = localUser();
        given(userService.findByEmail("user@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("raw-pw", "encoded-pw")).willReturn(true);
        given(passwordEncoder.encode("new-pw")).willReturn("encoded-new-pw");

        service.changePassword("user@test.com", "raw-pw", "new-pw");

        // 평문이 그대로 들어가면 안 된다
        assertThat(user.getPassword()).isEqualTo("encoded-new-pw");
        verify(userService).saveUser(user);
    }

    @Test
    @DisplayName("비밀번호 변경 - 존재하지 않는 사용자면 거부한다")
    void changePasswordForUnknownUserIsRejected() {
        given(userService.findByEmail("nobody@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword("nobody@test.com", "raw-pw", "new-pw"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
        verifyNoInteractions(passwordEncoder);
    }

    // ===== 탈퇴 =====

    @Test
    @DisplayName("탈퇴 - 데이터를 지우지 않고 비활성화만 한다(소프트 삭제)")
    void withdrawDisablesAccountWithoutDeleting() {
        User user = localUser();
        given(userService.findByEmail("user@test.com")).willReturn(Optional.of(user));

        service.withdraw("user@test.com");

        assertThat(user.isEnabled()).isFalse();
        verify(userService).saveUser(user);
    }

    @Test
    @DisplayName("탈퇴 - 존재하지 않는 사용자면 거부한다")
    void withdrawForUnknownUserIsRejected() {
        given(userService.findByEmail("nobody@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw("nobody@test.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
        verify(userService, never()).saveUser(any());
    }

    // ===== 중복 확인 =====

    @Test
    @DisplayName("중복 확인 - 조회 결과를 그대로 전달한다")
    void checkEmailDuplicateDelegates() {
        given(userService.existsByEmail("user@test.com")).willReturn(true);

        assertThat(service.checkEmailDuplicate("user@test.com")).isTrue();
    }
}
