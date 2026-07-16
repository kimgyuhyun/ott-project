package com.ottproject.ottbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * VerificationEmailService 단위 테스트
 *
 * 지키려는 규칙(인증 코드)
 * - 코드는 6자리 숫자다(프론트 입력 폼과 계약)
 * - 틀린 코드/발송한 적 없는 이메일은 인증되지 않는다
 * - 코드는 일회용이다: 한 번 성공하면 같은 코드로 다시 인증할 수 없다
 *   → 재사용이 되면 유출된 코드로 계정을 반복 인증할 수 있다
 * - 재발송하면 이전 코드는 무효가 된다
 *
 * 알려진 미구현(이 테스트 범위 밖)
 * - 메일 본문은 "10분간 유효"라고 안내하지만 만료가 구현돼 있지 않다(코드에 발급 시각이 없음).
 *   TTL 을 실제로 구현한 뒤 만료 테스트를 추가해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class VerificationEmailServiceTest {

    @Mock private JavaMailSender mailSender;

    @InjectMocks
    private VerificationEmailService service;

    @BeforeEach
    void setUp() {
        // @Value 필드는 단위테스트에서 주입되지 않는다
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@test.com");
    }

    /** 발송된 메일 본문에서 인증 코드를 꺼낸다(코드는 무작위라 캡처가 유일한 확인 경로) */
    private String sendAndCaptureCode(String email) {
        service.sendVerificationEmail(email);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
        String body = captor.getValue().getText();
        Matcher m = Pattern.compile("인증 코드: (\\d+)").matcher(body);
        assertThat(m.find()).as("메일 본문에 인증 코드가 있어야 한다").isTrue();
        return m.group(1);
    }

    @Test
    @DisplayName("발송 - 인증 코드는 6자리 숫자다")
    void sendsSixDigitNumericCode() {
        String code = sendAndCaptureCode("user@test.com");

        assertThat(code).hasSize(6).containsOnlyDigits();
    }

    @Test
    @DisplayName("발송 - 수신자/발신자가 설정된 메일이 나간다")
    void sendsMailToRequestedAddress() {
        service.sendVerificationEmail("user@test.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("user@test.com");
        assertThat(captor.getValue().getFrom()).isEqualTo("noreply@test.com");
    }

    @Test
    @DisplayName("검증 성공 - 발송된 코드와 일치하면 인증 완료 상태가 된다")
    void verifyCodeSucceedsWithMatchingCode() {
        String code = sendAndCaptureCode("user@test.com");

        assertThat(service.verifyCode("user@test.com", code)).isTrue();
        assertThat(service.isEmailVerified("user@test.com")).isTrue();
    }

    @Test
    @DisplayName("검증 실패 - 틀린 코드는 거부되고 인증 상태도 남지 않는다")
    void verifyCodeFailsWithWrongCode() {
        String code = sendAndCaptureCode("user@test.com");
        String wrong = code.equals("000000") ? "111111" : "000000";

        assertThat(service.verifyCode("user@test.com", wrong)).isFalse();
        assertThat(service.isEmailVerified("user@test.com")).isFalse();
    }

    @Test
    @DisplayName("검증 실패 - 발송한 적 없는 이메일은 어떤 코드로도 인증되지 않는다")
    void verifyCodeFailsForEmailWithoutSentCode() {
        assertThat(service.verifyCode("nobody@test.com", "123456")).isFalse();
        assertThat(service.isEmailVerified("nobody@test.com")).isFalse();
    }

    @Test
    @DisplayName("일회용 - 성공한 코드는 재사용할 수 없다(유출된 코드 반복 사용 방지)")
    void verifiedCodeCannotBeReused() {
        String code = sendAndCaptureCode("user@test.com");
        assertThat(service.verifyCode("user@test.com", code)).isTrue();

        // 같은 코드로 두 번째 시도
        assertThat(service.verifyCode("user@test.com", code)).isFalse();
    }

    @Test
    @DisplayName("재발송 - 새 코드가 발급되면 이전 코드는 무효가 된다")
    void resendInvalidatesPreviousCode() {
        String first = sendAndCaptureCode("user@test.com");
        String second = sendAndCaptureCode("user@test.com");
        // 무작위 코드가 우연히 같으면 검증 자체가 성립하지 않으므로 건너뛴다
        org.junit.jupiter.api.Assumptions.assumeFalse(first.equals(second));

        assertThat(service.verifyCode("user@test.com", first)).isFalse();
        assertThat(service.verifyCode("user@test.com", second)).isTrue();
    }

    @Test
    @DisplayName("다른 이메일로 발송된 코드로는 인증할 수 없다")
    void codeIsBoundToItsEmail() {
        String code = sendAndCaptureCode("user@test.com");

        assertThat(service.verifyCode("attacker@test.com", code)).isFalse();
        assertThat(service.isEmailVerified("attacker@test.com")).isFalse();
    }

    @Test
    @DisplayName("인증한 적 없는 이메일의 인증 여부는 false 다")
    void unverifiedEmailIsFalseByDefault() {
        assertThat(service.isEmailVerified("user@test.com")).isFalse();
    }
}
