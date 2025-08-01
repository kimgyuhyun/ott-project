package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.EmailVerification;
import com.ottproject.ottbackend.repository.EmailVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {
    
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailService emailService;
    
    private static final int VERIFICATION_CODE_LENGTH = 6;
    private static final int VERIFICATION_EXPIRY_MINUTES = 10;
    
    public void sendVerificationCode(String email) {
        // 기존 인증 정보 삭제
        emailVerificationRepository.deleteById(email);
        
        // 새로운 인증 코드 생성
        String verificationCode = generateVerificationCode();
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(VERIFICATION_EXPIRY_MINUTES);
        
        // 인증 정보 저장
        EmailVerification emailVerification = EmailVerification.builder()
                .email(email)
                .verificationCode(verificationCode)
                .expiryTime(expiryTime)
                .verified(false)
                .build();
        
        emailVerificationRepository.save(emailVerification);
        
        // 이메일 발송
        emailService.sendVerificationEmail(email, verificationCode);
        
        log.info("Verification code sent to: {}", email);
    }
    
    public boolean verifyCode(String email, String verificationCode) {
        Optional<EmailVerification> optional = emailVerificationRepository
                .findByEmailAndVerificationCode(email, verificationCode);
        
        if (optional.isEmpty()) {
            return false;
        }
        
        EmailVerification emailVerification = optional.get();
        
        // 만료 시간 확인
        if (LocalDateTime.now().isAfter(emailVerification.getExpiryTime())) {
            emailVerificationRepository.delete(emailVerification);
            return false;
        }
        
        // 인증 완료 처리
        emailVerification.setVerified(true);
        emailVerificationRepository.save(emailVerification);
        
        log.info("Email verified successfully: {}", email);
        return true;
    }
    
    public boolean isEmailVerified(String email) {
        Optional<EmailVerification> optional = emailVerificationRepository.findByEmail(email);
        return optional.isPresent() && optional.get().isVerified();
    }
    
    private String generateVerificationCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        
        for (int i = 0; i < VERIFICATION_CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        
        return code.toString();
    }
} 