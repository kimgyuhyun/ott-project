package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.AuthProvider;
import com.ottproject.ottbackend.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 사용자 엔티티
 *
 * 큰 흐름
 * - 로컬/소셜 로그인 사용자 계정을 저장한다.
 * - 권한/활성/이메일 인증 여부 등 보안 메타를 함께 보관한다.
 * - 생성/수정 시각은 Auditing 으로 자동 관리한다.
 *
 * 필드 개요
 * - id/email/password/name: 기본 계정 정보
 * - role: 권한(USER/ADMIN)
 * - authProvider/providerId: 인증 제공자/외부 식별자
 * - emailVerified/enabled: 인증/활성 상태
 * - createdAt/updatedAt: 생성/수정 시각
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder // 빌더 패턴
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 모든 필드 생성자 (빌더와 함께 필요)
@EntityListeners(AuditingEntityListener.class)
public class User { // 엔티티 시작

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 사용자 고유 ID

    @Column(unique = true, nullable = false)
    private String email; // 이메일 (고유값, 필수)

    @Column(nullable = true)            // 소셜 로그인은 비밀번호 없음
    private String password; // 비밀번호 (자체 로그인용)

    @Column(nullable = false)
    private String name; // 사용자 이름 (필수)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRole role = UserRole.USER; // 사용자 권한 (기본값: USER)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL; // 인증 제공자 (기본값: LOCAL)

    @Column(nullable = true)
    private String providerId; // 소셜 로그인 ID (소셜 로그인용)

    @Column(nullable = false)
    @Builder.Default
    private boolean emailVerified = false; // 이메일 인증 여부 (기본값: false)

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true; // 계정 활성화 여부 (기본값: true)

    @CreatedDate
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(); // 생성일시 (자동 생성)

    @LastModifiedDate
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now(); // 수정일시 (자동 업데이트)

}