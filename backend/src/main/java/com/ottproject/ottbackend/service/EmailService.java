package com.ottproject.ottbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    
    private final JavaMailSender mailSender;
    
    public void sendVerificationEmail(String to, String verificationCode) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("kgh9806@naver.com");
            message.setTo(to);
            message.setSubject("[OTT Project] 이메일 인증 코드");
            message.setText("안녕하세요! OTT Project입니다.\n\n" +
                    "이메일 인증 코드는 다음과 같습니다:\n\n" +
                    "인증 코드: " + verificationCode + "\n\n" +
                    "이 코드는 10분간 유효합니다.\n" +
                    "본인이 요청하지 않은 경우 이 메일을 무시하세요.\n\n" +
                    "감사합니다.");
            
            mailSender.send(message);
            log.info("Verification email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send verification email to: {}", to, e);
            throw new RuntimeException("이메일 발송에 실패했습니다.", e);
        }
    }
} 