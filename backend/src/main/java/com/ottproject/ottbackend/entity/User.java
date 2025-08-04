package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.AuthProvider;
import com.ottproject.ottbackend.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 사용자 정보를 저장하는 엔티티
 * 자체 로그인과 소셜 로그인을 모두 지원
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                    // 사용자 고유 ID

    @Column(unique = true, nullable = false)
    private String email;               // 이메일 (고유값, 필수)

    @Column(nullable = true)            // 소셜 로그인은 비밀번호 없음
    private String password;            // 비밀번호 (자체 로그인용)

    @Column(nullable = false)
    private String name;                // 사용자 이름 (필수)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;  // 사용자 권한 (기본값: USER)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider authProvider = AuthProvider.LOCAL;  // 인증 제공자 (기본값: LOCAL)

    @Column(nullable = true)
    private String providerId;          // 소셜 로그인 ID (소셜 로그인용)

    @Column(nullable = false)
    private boolean emailVerified = false;  // 이메일 인증 여부 (기본값: false)

    @Column(nullable = false)
    private boolean enabled = true;     // 계정 활성화 여부 (기본값: true)

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt;    // 생성일시 (자동 생성)

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;    // 수정일시 (자동 업데이트)
}