package com.ottproject.ottbackend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ottproject.ottbackend.dto.AuthLoginRequestDto;
import com.ottproject.ottbackend.dto.ChangePasswordRequestDto;
import com.ottproject.ottbackend.security.UserSessionRegistry;
import com.ottproject.ottbackend.service.AuthEventService;
import com.ottproject.ottbackend.service.EmailAuthService;
import com.ottproject.ottbackend.service.LoginAttemptService;
import com.ottproject.ottbackend.service.TurnstileVerifier;
import com.ottproject.ottbackend.service.VerificationEmailService;
import com.ottproject.ottbackend.util.SecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

/**
 * EmailAuthController 단위 테스트(비밀번호 변경, 로그인 시도 제한)
 *
 * 여기서 고정하는 규칙(PLATFORM 4절)
 * - 비밀번호 변경이 성공하면 그 사용자의 다른 세션을 끊는다. 탈취된 세션이 살아 있으면 변경은 아무것도 막지 못한다.
 * - 현재 비밀번호가 틀려 변경이 실패하면 세션을 건드리지 않는다.
 * - 로그인 시도는 사람 확인을 통과한 뒤에 센다. 사람 확인 실패까지 세면 봇이 토큰 없이 요청만 보내 남의 계정을 잠근다.
 * - 시도 상한을 넘긴 로그인은 비밀번호를 비교하지 않는다. 동시 요청에서 상한이 지켜지는지는 LoginAttemptLimitTest 가 본다.
 */
@ExtendWith(MockitoExtension.class)
class EmailAuthControllerTest {

    private static final Long USER_ID = 7L;
    private static final String EMAIL = "user@example.com";

    @Mock
    private EmailAuthService emailAuthService;

    @Mock
    private VerificationEmailService verificationEmailService;

    @Mock
    private AuthEventService authEventService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private TurnstileVerifier turnstileVerifier;

    @Mock
    private UserSessionRegistry userSessionRegistry;

    @Mock
    private SecurityUtil securityUtil;

    private EmailAuthController controller;
    private MockHttpSession session;
    private ChangePasswordRequestDto request;

    @BeforeEach
    void setUp() {
        controller = new EmailAuthController(
                emailAuthService,
                verificationEmailService,
                authEventService,
                loginAttemptService,
                turnstileVerifier,
                userSessionRegistry,
                securityUtil);
        session = new MockHttpSession();
        session.setAttribute("userEmail", EMAIL);
        request = new ChangePasswordRequestDto();
        request.setCurrentPassword("old-password");
        request.setNewPassword("new-password");
    }

    @Test
    @DisplayName("비밀번호 변경이 성공하면 현재 세션을 뺀 다른 세션을 끊는다")
    void revokesOtherSessionsAfterPasswordChange() {
        given(securityUtil.getCurrentUserIdOrNull(session)).willReturn(USER_ID);

        controller.changePassword(session, request);

        verify(emailAuthService).changePassword(EMAIL, "old-password", "new-password");
        verify(userSessionRegistry).revokeOthersKeepingCurrent(USER_ID, session.getId());
    }

    @Test
    @DisplayName("현재 비밀번호가 틀려 변경이 실패하면 세션을 건드리지 않는다")
    void keepsSessionsWhenPasswordChangeFails() {
        willThrow(new RuntimeException("현재 비밀번호가 올바르지 않습니다."))
                .given(emailAuthService)
                .changePassword(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> controller.changePassword(session, request)).isInstanceOf(RuntimeException.class);

        verify(userSessionRegistry, never()).revokeOthersKeepingCurrent(any(), any());
    }

    // ===== 로그인 시도 제한 =====

    private void login() {
        AuthLoginRequestDto login = new AuthLoginRequestDto();
        login.setEmail(EMAIL);
        login.setPassword("wrong-password");
        controller.login(login, session, new MockHttpServletRequest(), new MockHttpServletResponse());
    }

    @Test
    @DisplayName("사람 확인에 실패한 로그인은 시도로 세지 않는다 - 봇이 토큰 없이 요청만 보내 남의 계정을 잠그지 못한다")
    void failedTurnstileIsNotCountedAsAttempt() {
        given(loginAttemptService.isChallengeRequired(EMAIL)).willReturn(true);
        given(turnstileVerifier.verify(any())).willReturn(false);

        assertThatThrownBy(this::login).isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(
                        e.getStatusCode().value())
                .isEqualTo(401));

        verify(loginAttemptService, never()).tryAcquireAttempt(anyString());
        verify(emailAuthService, never()).login(anyString(), anyString());
    }

    @Test
    @DisplayName("시도 상한을 넘기면 비밀번호를 비교하지 않고 429 로 거부한다")
    void attemptOverLimitSkipsPasswordCheck() {
        given(loginAttemptService.tryAcquireAttempt(EMAIL)).willReturn(false);

        assertThatThrownBy(this::login).isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(
                        e.getStatusCode().value())
                .isEqualTo(429));

        verify(emailAuthService, never()).login(anyString(), anyString());
    }
}
