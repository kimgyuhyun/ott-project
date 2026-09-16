package com.ottproject.ottbackend.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import org.springframework.mock.web.MockHttpSession;

/**
 * EmailAuthController 비밀번호 변경 단위 테스트
 *
 * 여기서 고정하는 규칙(PLATFORM 4절)
 * - 비밀번호 변경이 성공하면 그 사용자의 다른 세션을 끊는다. 탈취된 세션이 살아 있으면 변경은 아무것도 막지 못한다.
 * - 현재 비밀번호가 틀려 변경이 실패하면 세션을 건드리지 않는다.
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
}
