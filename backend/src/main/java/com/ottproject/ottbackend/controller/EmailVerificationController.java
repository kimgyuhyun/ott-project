package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.EmailVerificationRequest;
import com.ottproject.ottbackend.dto.EmailVerificationResponse;
import com.ottproject.ottbackend.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationController {
    
    private final EmailVerificationService emailVerificationService;
    
    @PostMapping("/send-verification")
    public ResponseEntity<EmailVerificationResponse> sendVerificationCode(@RequestParam String email) {
        try {
            emailVerificationService.sendVerificationCode(email);
            
            EmailVerificationResponse response = EmailVerificationResponse.builder()
                    .success(true)
                    .message("인증 코드가 이메일로 발송되었습니다.")
                    .email(email)
                    .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to send verification code to: {}", email, e);
            
            EmailVerificationResponse response = EmailVerificationResponse.builder()
                    .success(false)
                    .message("인증 코드 발송에 실패했습니다.")
                    .email(email)
                    .build();
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/verify")
    public ResponseEntity<EmailVerificationResponse> verifyCode(@RequestBody EmailVerificationRequest request) {
        try {
            boolean isVerified = emailVerificationService.verifyCode(request.getEmail(), request.getVerificationCode());
            
            if (isVerified) {
                EmailVerificationResponse response = EmailVerificationResponse.builder()
                        .success(true)
                        .message("이메일 인증이 완료되었습니다.")
                        .email(request.getEmail())
                        .build();
                
                return ResponseEntity.ok(response);
            } else {
                EmailVerificationResponse response = EmailVerificationResponse.builder()
                        .success(false)
                        .message("인증 코드가 올바르지 않거나 만료되었습니다.")
                        .email(request.getEmail())
                        .build();
                
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("Failed to verify code for: {}", request.getEmail(), e);
            
            EmailVerificationResponse response = EmailVerificationResponse.builder()
                    .success(false)
                    .message("인증 처리 중 오류가 발생했습니다.")
                    .email(request.getEmail())
                    .build();
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/check-verification")
    public ResponseEntity<EmailVerificationResponse> checkVerification(@RequestParam String email) {
        try {
            boolean isVerified = emailVerificationService.isEmailVerified(email);
            
            EmailVerificationResponse response = EmailVerificationResponse.builder()
                    .success(isVerified)
                    .message(isVerified ? "이메일이 인증되었습니다." : "이메일이 인증되지 않았습니다.")
                    .email(email)
                    .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to check verification for: {}", email, e);
            
            EmailVerificationResponse response = EmailVerificationResponse.builder()
                    .success(false)
                    .message("인증 상태 확인 중 오류가 발생했습니다.")
                    .email(email)
                    .build();
            
            return ResponseEntity.badRequest().body(response);
        }
    }
} 