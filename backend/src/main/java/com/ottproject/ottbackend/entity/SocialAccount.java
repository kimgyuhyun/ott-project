package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.AuthProvider;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 소셜 계정 연동 엔티티
 *
 * 큰 흐름
 * - 사용자와 소셜 제공자 계정의 연동 관계를 저장한다.
 * - (provider, provider_id) 복합 유니크로 중복 연동을 방지한다.
 *
 * 필드 개요
 * - id/user: 식별/소유 사용자
 * - provider/providerId: 제공자/외부 식별자
 * - emailVerified: 이메일 검증 여부(소셜 기본 true)
 * - createdAt: 연동 생성 시각
 */
@Entity // JPA 엔티티 선언
@Table( // 테이블 매핑
        name = "user_social_accounts", // 테이블명
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"}) // (provider, provider_id) 유니크
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class) // 생성 시각 자동 기록
public class SocialAccount { // 소셜 계정 연동 엔티티

    @Id // 기본키
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가
    private Long id; // PK

    @ManyToOne(fetch = FetchType.LAZY, optional = false) // 다대일: 사용자
    @JoinColumn(name = "user_id", nullable = false) // FK: user_id
    private User user; // 소유 사용자

    @Enumerated(EnumType.STRING) // ENUM → 문자열 저장
    @Column(name = "provider", nullable = false) // 제공자 컬럼
    private AuthProvider provider; // 인증 제공자(GOOGLE/KAKAO/NAVER/LOCAL 등)

    @Column(name = "provider_id", nullable = false) // 제공자별 고유 ID
    private String providerId; // 소셜 고유 식별자

    @Column(name = "email_verified", nullable = false) // 이메일 검증 여부
    private boolean emailVerified = true; // 기본 true(소셜)

    @CreatedDate // 생성 시각 자동
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // 연동 생성 시각

    // ===== 정적 팩토리 메서드 =====

    /**
     * 소셜 계정 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 사용자
     * @param provider 인증 제공자
     * @param providerId 제공자별 고유 ID
     * @param email 이메일
     * @return 생성된 SocialAccount 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static SocialAccount createSocialAccount(User user, AuthProvider provider, String providerId, String email) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (provider == null) {
            throw new IllegalArgumentException("인증 제공자는 필수입니다.");
        }
        if (providerId == null || providerId.trim().isEmpty()) {
            throw new IllegalArgumentException("제공자 ID는 필수입니다.");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }

        // SocialAccount 엔티티 생성
        SocialAccount socialAccount = new SocialAccount();
        socialAccount.user = user;
        socialAccount.provider = provider;
        socialAccount.providerId = providerId.trim();
        socialAccount.emailVerified = true; // 소셜 계정은 기본적으로 인증됨

        return socialAccount;
    }
}


