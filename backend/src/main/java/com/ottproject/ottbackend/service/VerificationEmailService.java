package com.ottproject.ottbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

/**
 * VerificationEmailService
 *
 * 큰 흐름
 * - 인증 코드 메일 발송/검증과 비밀번호 재설정 메일 발송을 제공한다.
 * - 코드/인증완료 티켓은 Redis 에 TTL 과 함께 저장한다.
 *
 * 저장소를 Redis 로 두는 이유(과거: ConcurrentHashMap 인메모리)
 * - 만료: 인메모리 맵에는 발급 시각이 없어 "10분간 유효" 안내가 지켜지지 않았다(코드가 영구 유효).
 *   Redis TTL 로 만료를 저장소에 위임한다.
 * - 누수: 검증에 실패하거나 가입을 포기한 코드가 맵에 영원히 남았고, 인증완료 맵은 지우는 코드가
 *   아예 없었다. TTL 이 붙으면 저절로 정리된다.
 * - 재시작/다중 인스턴스: 세션과 마찬가지로 인스턴스 간 공유되고 재배포에도 살아남아야 한다.
 *   (가입 2단계와 3단계가 다른 인스턴스로 갈 수 있다)
 *
 * 실패 정책
 * - Redis 장애 시 예외를 그대로 전파한다(fail-closed). 장애가 인증 우회 통로가 되면 안 된다.
 *
 * 메서드 개요
 * - sendVerificationEmail: 인증 코드 발송
 * - verifyCode: 인증 코드 검증(성공 시 코드 소비 + 인증완료 티켓 발급)
 * - isEmailVerified: 인증 여부 확인
 * - consumeVerification: 인증완료 티켓 소비(가입 확정 시 호출)
 * - sendPasswordResetEmail: 비밀번호 재설정 메일 발송
 */
@Service
@RequiredArgsConstructor
public class VerificationEmailService {

    private final JavaMailSender mailSender; // spring boot mail 자동 주입 (SMTP 서버 연결용)
    private final StringRedisTemplate redisTemplate; // 코드/인증완료 티켓 저장소

    @Value("${spring.mail.username}") // application-dev.yml 에서 발신자 이메일 주소 주입
    private String fromEmail; // 발신자 이메일 주소

    private static final String CODE_KEY_PREFIX = "ott:email-verification:v1:code:"; // 이메일 -> 인증 코드
    private static final String VERIFIED_KEY_PREFIX = "ott:email-verification:v1:verified:"; // 이메일 -> 인증완료 티켓

    private static final Duration CODE_TTL = Duration.ofMinutes(10); // 메일 본문 안내와 반드시 같아야 한다
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(30); // 인증 직후 가입을 마치기에 충분한 창

    public void sendVerificationEmail(String to) {
        String verificationCode = generateVerificationCode();
        // TTL 과 함께 저장: 만료 판정을 Redis 에 맡긴다(인메모리 시절엔 발급 시각이 없어 만료가 불가능했다)
        redisTemplate.opsForValue().set(codeKey(to), verificationCode, CODE_TTL);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail); // 발신자 이메일 주소를 설정함
        message.setTo(to); // 수신자 이메일 주소를 설정함
        message.setSubject("OTT 프로젝트 인증 코드"); // 메일 제목을 설정함
        // 유효시간 안내는 CODE_TTL 에서 파생시킨다. 안내와 실제 만료가 갈라진 것이 이 클래스의 원래 버그였다.
        message.setText("이메일 인증 코드: " + verificationCode + "\n\n"
                + "이 코드를 입력하여 이메일 인증을 완료해주세요.\n"
                + "인증 코드는 " + CODE_TTL.toMinutes() + "분간 유효합니다."); // 메일 본문을 설정함

        mailSender.send(message); // SMTP 서버를 통해 이메일 발송함
    }

    public boolean verifyCode(String email, String code) {
        String storedCode = redisTemplate.opsForValue().get(codeKey(email));
        // 만료됐거나 발송한 적 없으면 키가 없다(둘을 구분해 알려주지 않는다)
        if (storedCode == null || !storedCode.equals(code)) {
            return false;
        }
        redisTemplate.delete(codeKey(email)); // 일회용: 성공한 코드는 즉시 소비한다
        redisTemplate.opsForValue().set(verifiedKey(email), "1", VERIFIED_TTL); // 인증완료 티켓 발급
        return true;
    }

    public boolean isEmailVerified(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(verifiedKey(email)));
    }

    /**
     * 인증완료 티켓 소비
     * - 가입이 확정되면 호출한다. 티켓 하나로 여러 계정을 만들지 못하게 한다.
     */
    public void consumeVerification(String email) {
        redisTemplate.delete(verifiedKey(email));
    }

    private String codeKey(String email) {
        return CODE_KEY_PREFIX + normalize(email);
    }

    private String verifiedKey(String email) {
        return VERIFIED_KEY_PREFIX + normalize(email);
    }

    /**
     * 키 정규화
     * - User.createLocalUser 가 이메일을 trim + toLowerCase 해서 저장하므로 계정의 신원은 소문자 이메일이다.
     *   티켓도 같은 신원으로 키를 잡아야 발송/검증/가입이 같은 대상을 가리킨다.
     */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String generateVerificationCode() {
        // 6자리 인증 코드 생성 (0~9 숫자로 구성) 외부에선 쓰지 않으므로 private로 선언 String타입을 반환함
        Random random = new Random(); // 랜덤 객체 생성
        StringBuilder code = new StringBuilder(); // 인증 코드를 넣을 빈 버퍼 생성성
        for (int i = 0; i < 6; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString(); // 버퍼에 쌓인 6자리 숫자를 String으로 변환해서 반환함
    }

    public void sendPasswordResetEmail(String to, String resetToken) {
        // 비밀번호 재설정 메일 발송 메서드 파라미터로 to와 resetToken을 String 타입으로 받고 리턴값은 없음
        // resetToken에는 비밀번호 재설정 요청을 대표하는 고유한 보유 문자열이 담겨 들어옴
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail); // 발신자 이메일 주소를 설정함
        message.setTo(to); // 수신자 이메일 주소를 설정함
        message.setSubject("OTT 프로젝트 비밀번호 재설정"); // 메일 제목을 설정함
        String origin = System.getenv("FRONTEND_ORIGIN"); // env파일에 프론트엔드 서비스의 public 주소를 저장
        // System.getenv()는 JVM이 실행 중인 환경에서 설정된 시스템 환경 변수를 조회함
        // 여기서는 비밀번호 재설정 링크를 만들 때 먼저 프론트엔드 서비스의 pbulic 주소(FRONTEND_ORIGIN)을 조회하고
        // 값이 있으면 오리진 주소를 쓰고 없으면 다른값으로 대체함
        if (origin == null || origin.isBlank()) {
            // 만약 origin이 null이거나 빈 문자열이면
            origin = System.getenv("BACKEND_PUBLIC_ORIGIN");
            // BACKEND_PUBLIC_ORIGIN 환경 변수에 저장된 백엔드 서비스의 public 주소를 조회하고 할당함
        }
        // origin에 값이 프론트 오리진, 백엔드 오리진, 아니면 아예 값이 없는 상태로 실행
        String resetBase = (origin != null && !origin.isBlank()) ? origin : ("http://" + "127.0.0.1:8090");
        // origin에 값이 null이 아니고 origin이 빈문자열이 아니면, 즉 값이 있으면
        // 프론트나 백엔드 오리진값이면 그대로 쓰고 아예 값이 없는 상태면 127.0.0.1:8090 주소를 씀
        message.setText("비밀번호를 재설정하려면 다음 링크를 클릭하세요:\n\n"
                + resetBase.replaceAll("/+$", "") + "/api/auth/reset-password?token=" + resetToken + "\n\n"
                + "이 링크는 1시간 동안 유효합니다.");
                // repleaceAll("/+$", "")는 정규식을 이용해 URL 끝에 붙어있는 슬래시를 모두 잘라내는 코드임
                // /+$ 정규식 뜻은 문자열 끝($)에서 하나 이상의 슬래시(/+)를 찾는 패턴
                // 이걸 인자로 태워보내면 찾은 슬래쉬들을 빈 문자열로 대체함 즉 제거하는것
                // 그다음 URL 끝에 resetToken을 파라미터로 추가하고 1시간동안 유효하다는걸 알림
        mailSender.send(message); // SMTP 서버를 통해 이메일 발송함
    }
}
