package com.ottproject.ottbackend.dto;

import lombok.Data;

@Data
public class EmailVerificationRequest {
    private String email;
    private String verificationCode;
} 