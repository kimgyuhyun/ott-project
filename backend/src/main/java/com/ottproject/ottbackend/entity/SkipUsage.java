package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.SkipType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 스킵 사용 로그 엔티티
 *
 * 큰 흐름
 * - 인트로/엔딩 스킵 사용 이력을 보관한다.
 * - 비로그인도 기록할 수 있도록 user nullable.
 *
 * 필드 개요
 * - id/user/episode: 식별/사용자/대상 회차
 * - type/atSec: 스킵 유형/사용 시점(초)
 * - createdAt: 생성 시각
 */
@Entity
@Table(name = "skip_usage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SkipUsage { // 스킵 사용 로그

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK

    // 사용한 사용자(null 가능)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id")
    private User user; // null 허용(비로그인)

    // 대상 에피소드
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode; // 대상

    @Enumerated(EnumType.STRING)
    @Column(name = "skip_type", nullable = false)
    private com.ottproject.ottbackend.enums.SkipType type; // INTRO/OUTRO

    @Column(name = "position_sec", nullable = false)
    private Integer atSec; // 시점(초)

    @CreatedDate
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt; // 생성 시각

    // ===== 정적 팩토리 메서드 =====

    /**
     * 스킵 사용 로그 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 사용자 (null 허용)
     * @param episode 에피소드
     * @param skipType 스킵 유형
     * @return 생성된 SkipUsage 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static SkipUsage createSkipUsage(User user, Episode episode, SkipType skipType) {
        // 필수 필드 검증
        if (episode == null) {
            throw new IllegalArgumentException("에피소드는 필수입니다.");
        }
        if (skipType == null) {
            throw new IllegalArgumentException("스킵 유형은 필수입니다.");
        }

        // SkipUsage 엔티티 생성
        SkipUsage skipUsage = new SkipUsage();
        skipUsage.user = user; // null 허용
        skipUsage.episode = episode;
        skipUsage.type = skipType;
        skipUsage.atSec = 0; // 기본값, 나중에 업데이트

        return skipUsage;
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 스킵 시점 설정
     * @param positionSec 스킵 시점 (초)
     * @throws IllegalArgumentException 시점이 유효하지 않은 경우
     */
    public void setSkipPosition(Integer positionSec) {
        if (positionSec == null || positionSec < 0) {
            throw new IllegalArgumentException("스킵 시점은 0 이상이어야 합니다.");
        }

        this.atSec = positionSec;
    }
}


