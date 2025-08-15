package com.ottproject.ottbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 이메일 발송/인증 서비스
 * - 인증 코드 발송/검증, 비밀번호 재설정 메일 지원
 * - 실제 운영은 Redis 등 외부 저장소 사용 권장
 */
@Service // Bean 으로 등록
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성
public class VerificationEmailService {

    private final JavaMailSender mailSender; // spring boot mail 자동 주입 (SMTP 서버 연결용)

    @Value("${spring.mail.username}") // application-dev.yml 에서 발신자 이메일 주소 주입
    private String fromEmail;

    // 임시 저장소 (실제로는 Redis 사용 권장) - 인증 코드와 이메일 매핑
    private final ConcurrentHashMap<String, String> emailVerificationCodes = new ConcurrentHashMap<>();
    // 임시 저장소 (실제로는 Redis 사용 권장) - 인증 완료된 이메일 목록
    private final ConcurrentHashMap<String, Boolean> verifiedEmails = new ConcurrentHashMap<>();

    // 이메일 인증 코드 발송 메서드
    public void sendVerificationEmail(String to) {
        //6자리 인증 코드 생성 (0~9 숫자로 구성)
        String verificationCode = generateVerificationCode(); // 6자리 코드 생성
        emailVerificationCodes.put(to, verificationCode); // 이메일과 코드 매핑 저장

        SimpleMailMessage message = new SimpleMailMessage(); // 텍스트 메일
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("OTT 프로젝트 인증 코드");
        message.setText("이메일 인증 코드: " + verificationCode + "\n\n"
                + "이 코드를 입력하여 이메일 인증을 완료해주세요.\n"
                + "인증 코드는 10분간 유효합니다.");

        mailSender.send(message); // SMTP 발송
    }


    // 인증 코드 확인 메서드
    public boolean verifyCode(String email, String code) {
        String storedCode = emailVerificationCodes.get(email); // 저장된 인증 코드 조회
        if (storedCode != null && storedCode.equals(code)) { // 입력된 코드와 저장된 코드 비교
            verifiedEmails.put(email, true); // 인증 완료 처리
            emailVerificationCodes.remove(email); // 사용된 인증 코드 제거 (보안상)
            return true; // 인증 성공
        }
        return false; // 인증 실패
    }

    // 이메일 인증 여부 확인 메서드
    public boolean isEmailVerified(String email) {
        return verifiedEmails.getOrDefault(email, false); // 인증 완료된 이메일인지 확인
    }

    //6자리 인증 코드 생성 메서드 (0~9 숫자로 구성)
    private String generateVerificationCode() {
        Random random = new Random(); // 랜덤 객체 생성
        StringBuilder code = new StringBuilder(); // 문자열 빌더로 코드 생성
        for (int i = 0; i < 6; i++) { // 6자리 코드 생성
            code.append(random.nextInt(10)); // 0~9 사이의 랜덤 숫자 추가
        }
        return code.toString(); // 생성된 코드 반환
    }
    // 비밀번호 재설정 메일 발송 메서드
    public void sendPasswordResetEmail(String to, String resetToken) {
        SimpleMailMessage message = new SimpleMailMessage(); // 간단한 텍스트 메일 객체 생성
        message.setFrom(fromEmail);
        message.setTo(to); // 수신자 이메일 주소 설정
        message.setSubject("OTT 프로젝트 비밀번호 재설정"); // 메일 제목 설정
        message.setText("비밀번호를 재설정하려면 다음 링크를 클릭하세요:\n\n"
                + "http://localhost:8090/api/auth/reset-password?token=" + resetToken + "\n\n"
                + "이 링크는 1시간 동안 유효합니다."); // 메일 본문 내용 설정 (비밀번호 재설정 링크 포함)

        mailSender.send(message); // SMTP 서버를 통해 이메일 발송
    }
}
