package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.AuthProvider;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity // JPA 엔티티 선언
@Table( // 테이블 매핑
        name = "user_social_accounts", // 테이블명
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"}) // (provider, provider_id) 유니크
)
@Getter
@Setter
@Builder
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
    @Builder.Default
    private boolean emailVerified = true; // 기본 true(소셜)

    @CreatedDate // 생성 시각 자동
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // 연동 생성 시각
}


