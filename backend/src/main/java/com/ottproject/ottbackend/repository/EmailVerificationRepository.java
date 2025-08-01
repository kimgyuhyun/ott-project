package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, String> {
    Optional<EmailVerification> findByEmailAndVerificationCode(String email, String verificationCode);
    Optional<EmailVerification> findByEmail(String email);
} 