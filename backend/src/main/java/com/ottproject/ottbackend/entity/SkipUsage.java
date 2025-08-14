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
 * - 비로그인 사용도 기록 가능(user null)
 */
@Entity
@Table(name = "skip_usage")
@Getter
@Setter
@Builder
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
    @Column(nullable = false)
    private com.ottproject.ottbackend.enums.SkipType type; // INTRO/OUTRO

    @Column(nullable = false)
    private Integer atSec; // 시점(초)

    @CreatedDate
    @Column(nullable = false)
    @Builder.Default
    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now(); // 생성 시각
}


