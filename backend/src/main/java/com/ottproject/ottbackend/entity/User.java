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
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 모든 필드 생성자
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 사용자 고유 ID (DB에서 자동 생성)

    @Column(unique = true, nullable = false)
    private String email; // 이메일 (고유값, 필수)

    @Column(nullable = true)            // 소셜 로그인은 비밀번호 없음
    private String password; // 비밀번호 (자체 로그인용)

    @Column(nullable = false)
    private String name; // 사용자 이름 (필수)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER; // 사용자 권한 (기본값: USER)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider authProvider = AuthProvider.LOCAL; // 인증 제공자 (기본값: LOCAL)

    @Column(nullable = true)
    private String providerId; // 소셜 로그인 ID (소셜 로그인용)

    @Column(nullable = false)
    private boolean emailVerified = false; // 이메일 인증 여부 (기본값: false)

    @Column(nullable = false)
    private boolean enabled = true; // 계정 활성화 여부 (기본값: true)

    @Column(name = "profile_image")
    private String profileImage; // 프로필 이미지 URL(옵션)

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt; // 생성일시 (자동 생성)

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt; // 수정일시 (자동 업데이트)

    // ===== 정적 팩토리 메서드 =====
    /**
     * 로컬 사용자 생성 (이메일/비밀번호 기반)
     * 
     * @param email 이메일 (필수)
     * @param password 비밀번호 (필수)
     * @param name 사용자 이름 (필수)
     * @return 생성된 User 엔티티
     */
    public static User createLocalUser(String email, String password, String name) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 이름은 필수입니다.");
        }
        
        User user = new User();
        user.email = email.trim().toLowerCase();
        user.password = password;
        user.name = name.trim();
        user.role = UserRole.USER;
        user.authProvider = AuthProvider.LOCAL;
        user.providerId = null;
        user.emailVerified = false;
        user.enabled = true;
        user.profileImage = null;
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();
        
        return user;
    }
    
    /**
     * 소셜 사용자 생성 (OAuth 기반)
     * 
     * @param email 이메일 (필수)
     * @param name 사용자 이름 (필수)
     * @param authProvider 인증 제공자 (필수)
     * @param providerId 외부 제공자 ID (필수)
     * @param profileImage 프로필 이미지 URL (선택)
     * @return 생성된 User 엔티티
     */
    public static User createSocialUser(String email, String name, AuthProvider authProvider, 
                                      String providerId, String profileImage) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 이름은 필수입니다.");
        }
        if (authProvider == null) {
            throw new IllegalArgumentException("인증 제공자는 필수입니다.");
        }
        if (providerId == null || providerId.trim().isEmpty()) {
            throw new IllegalArgumentException("제공자 ID는 필수입니다.");
        }
        
        User user = new User();
        user.email = email.trim().toLowerCase();
        user.password = null; // 소셜 로그인은 비밀번호 없음
        user.name = name.trim();
        user.role = UserRole.USER;
        user.authProvider = authProvider;
        user.providerId = providerId.trim();
        user.emailVerified = true; // 소셜 로그인은 이메일 인증 완료로 간주
        user.enabled = true;
        user.profileImage = profileImage != null ? profileImage.trim() : null;
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();
        
        return user;
    }
    
    /**
     * 관리자 사용자 생성
     * 
     * @param email 이메일 (필수)
     * @param password 비밀번호 (필수)
     * @param name 사용자 이름 (필수)
     * @return 생성된 User 엔티티
     */
    public static User createAdminUser(String email, String password, String name) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 이름은 필수입니다.");
        }
        
        User user = new User();
        user.email = email.trim().toLowerCase();
        user.password = password;
        user.name = name.trim();
        user.role = UserRole.ADMIN;
        user.authProvider = AuthProvider.LOCAL;
        user.providerId = null;
        user.emailVerified = true; // 관리자는 이메일 인증 완료로 간주
        user.enabled = true;
        user.profileImage = null;
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();
        
        return user;
    }
}