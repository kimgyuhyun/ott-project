package com.ottproject.ottbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * VerificationEmailService 단위 테스트
 *
 * 지키려는 규칙(인증 코드)
 * - 코드는 6자리 숫자다(프론트 입력 폼과 계약)
 * - 코드는 메일에 안내한 유효시간(10분)과 "같은" TTL 로 저장된다
 *   → 안내와 실제 만료가 갈라진 것이 이 클래스의 원래 버그였다(인메모리 맵 시절 만료 자체가 없었음)
 * - 인증완료 티켓도 유한한 TTL 을 갖는다(한 번 인증한 이메일이 영구히 인증 상태로 남으면 안 된다)
 * - 틀린 코드/만료·미발송 이메일은 인증되지 않는다
 * - 코드는 일회용이다: 성공 즉시 소비한다
 * - Redis 장애는 fail-closed 다: 우회 통로가 되면 안 된다
 *
 * 만료 자체(시간 경과)는 Redis 책임이라 여기서 시계를 돌리지 않는다.
 * 이 테스트가 지키는 것은 "몇 분짜리 TTL 로 넘겼는가"다.
 */
@ExtendWith(MockitoExtension.class)
class VerificationEmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private VerificationEmailService service;

    private static final String CODE_KEY = "ott:email-verification:v1:code:user@test.com";
    private static final String VERIFIED_KEY = "ott:email-verification:v1:verified:user@test.com";

    @BeforeEach
    void setUp() {
        // @Value 필드는 단위테스트에서 주입되지 않는다
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@test.com");
    }

    /** 발송된 메일 본문에서 인증 코드를 꺼낸다 */
    private String sentCode() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        Matcher m = Pattern.compile("인증 코드: (\\d+)").matcher(captor.getValue().getText());
        assertThat(m.find()).as("메일 본문에 인증 코드가 있어야 한다").isTrue();
        return m.group(1);
    }

    // ===== 발송 =====

    @Test
    @DisplayName("발송 - 인증 코드는 6자리 숫자이고, 메일에 담긴 코드가 그대로 저장된다")
    void sendsSixDigitCodeAndStoresTheSameOne() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        service.sendVerificationEmail("user@test.com");

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(CODE_KEY), stored.capture(), any(Duration.class));
        assertThat(stored.getValue()).hasSize(6).containsOnlyDigits();
        // 메일로 보낸 코드와 저장한 코드가 다르면 아무도 인증할 수 없다
        assertThat(stored.getValue()).isEqualTo(sentCode());
    }

    @Test
    @DisplayName("발송 - 코드는 10분 TTL 로 저장된다(메일 안내와 일치)")
    void storesCodeWithTenMinuteTtl() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        service.sendVerificationEmail("user@test.com");

        // 핵심: TTL 이 없거나 안내와 다르면 "10분간 유효" 안내가 거짓이 된다
        verify(valueOperations).set(eq(CODE_KEY), anyString(), eq(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("발송 - 메일 본문의 유효시간 안내가 실제 TTL 과 같다")
    void mailBodyStatesTheActualTtl() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        service.sendVerificationEmail("user@test.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("10분간 유효합니다");
        assertThat(captor.getValue().getTo()).containsExactly("user@test.com");
        assertThat(captor.getValue().getFrom()).isEqualTo("noreply@test.com");
    }

    @Test
    @DisplayName("발송 - 이메일 대소문자가 달라도 같은 키를 쓴다(계정 신원은 소문자 이메일)")
    void keyIsCaseInsensitive() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        service.sendVerificationEmail("  User@Test.com  ");

        // User.createLocalUser 가 trim+toLowerCase 로 계정을 만들므로 티켓도 같은 신원이어야 한다
        verify(valueOperations).set(eq(CODE_KEY), anyString(), any(Duration.class));
    }

    // ===== 검증 =====

    @Test
    @DisplayName("검증 성공 - 코드가 일치하면 코드를 소비하고 인증완료 티켓을 30분 TTL 로 발급한다")
    void verifyCodeConsumesCodeAndIssuesTicket() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CODE_KEY)).willReturn("123456");

        assertThat(service.verifyCode("user@test.com", "123456")).isTrue();

        verify(redisTemplate).delete(CODE_KEY); // 일회용
        // 티켓에 TTL 이 없으면 한 번 인증한 이메일이 영구히 인증 상태로 남는다
        verify(valueOperations).set(VERIFIED_KEY, "1", Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("검증 실패 - 틀린 코드는 거부하고 코드를 소비하지 않는다(오타로 코드가 날아가면 안 된다)")
    void verifyCodeFailsWithWrongCode() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CODE_KEY)).willReturn("123456");

        assertThat(service.verifyCode("user@test.com", "000000")).isFalse();

        verify(redisTemplate, never()).delete(anyString());
        verify(valueOperations, never()).set(eq(VERIFIED_KEY), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("검증 실패 - 만료됐거나 발송한 적 없는 이메일은 인증되지 않는다")
    void verifyCodeFailsWhenCodeIsGone() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CODE_KEY)).willReturn(null); // TTL 만료 후 == 발송 안 함

        assertThat(service.verifyCode("user@test.com", "123456")).isFalse();

        verify(valueOperations, never()).set(eq(VERIFIED_KEY), anyString(), any(Duration.class));
    }

    // ===== 인증 여부 / 티켓 소비 =====

    @Test
    @DisplayName("인증 여부 - 티켓이 있으면 true, 없으면 false")
    void isEmailVerifiedReflectsTicket() {
        given(redisTemplate.hasKey(VERIFIED_KEY)).willReturn(true);
        assertThat(service.isEmailVerified("user@test.com")).isTrue();

        given(redisTemplate.hasKey(VERIFIED_KEY)).willReturn(false);
        assertThat(service.isEmailVerified("user@test.com")).isFalse();
    }

    @Test
    @DisplayName("인증 여부 - Redis 가 null 을 주면 인증되지 않은 것으로 본다(fail-closed)")
    void isEmailVerifiedIsFalseOnNull() {
        given(redisTemplate.hasKey(VERIFIED_KEY)).willReturn(null);

        assertThat(service.isEmailVerified("user@test.com")).isFalse();
    }

    @Test
    @DisplayName("티켓 소비 - 가입 확정 시 티켓을 지운다(하나의 인증으로 여러 계정 방지)")
    void consumeVerificationDeletesTicket() {
        service.consumeVerification("user@test.com");

        verify(redisTemplate).delete(VERIFIED_KEY);
    }

    // ===== 실패 정책 =====

    @Test
    @DisplayName("Redis 장애는 통과가 아니라 실패다(fail-closed) - 장애가 인증 우회 통로가 되면 안 된다")
    void redisFailureDoesNotSilentlyPass() {
        given(redisTemplate.hasKey(VERIFIED_KEY)).willThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> service.isEmailVerified("user@test.com"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("코드 저장에 실패하면 메일을 보내지 않는다 - 검증 불가능한 코드를 보내면 안 된다")
    void doesNotSendMailWhenStoringCodeFails() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        willThrow(new RuntimeException("redis down"))
                .given(valueOperations).set(anyString(), anyString(), any(Duration.class));

        assertThatThrownBy(() -> service.sendVerificationEmail("user@test.com"))
                .isInstanceOf(RuntimeException.class);
        // 저장 안 된 코드를 메일로 보내면 사용자는 절대 인증할 수 없다
        verifyNoInteractions(mailSender);
    }
}
