package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 사용자 재생 설정 엔티티
 *
 * 큰 흐름
 * - 자동 스킵/기본 화질/자동 다음 화 설정을 1:1 로 보관한다.
 * - 마지막 수정 시각으로 동기화 시점을 관리한다.
 *
 * 필드 개요
 * - id/user: 식별/소유자(1:1)
 * - autoSkipIntro/autoSkipEnding/defaultQuality/autoNextEpisode: 재생 설정
 * - updatedAt: 최근 갱신 시각
 */
@Entity // 재생 설정 엔티티
@Table(name = "user_settings", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id"})) // 유니크
@Getter
@Setter
@Builder @NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserSettings { // 사용자 재생 설정

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) // PK
    private Long id; // PK

    @OneToOne(fetch = FetchType.LAZY, optional = false) // 1:1 사용자
    @JoinColumn(name = "user_id", nullable = false) // FK
    private User user; // 사용자

    @Column(nullable = false) @Builder.Default // 자동 스킵
    private Boolean autoSkipIntro = true; // 오프닝 자동 스킵

    @Column(nullable = false) @Builder.Default // 자동 스킵
    private Boolean autoSkipEnding = true; // 엔딩 자동 스킵

    @Column(nullable = false) @Builder.Default // 기본 화질
    private String defaultQuality = "auto"; // auto|480p|720p|1080p

    @Column(nullable = false) @Builder.Default // 다음 화 자동재생
    private Boolean autoNextEpisode = true; // 자동 다음 화

    @Column(nullable = true) @Builder.Default // 테마 설정 (null=설정안함)
    private String theme = null; // light|dark|null

    @Column(nullable = false) @Builder.Default // 언어 설정
    private String language = "ko"; // ko|en|ja

    @Column(nullable = false) @Builder.Default // 알림 설정
    private Boolean notifications = true; // 알림 활성화

    @Column(nullable = false) @Builder.Default // 자동 재생 설정
    private Boolean autoPlay = false; // 자동 재생

    @LastModifiedDate
    @Column(nullable = false) @Builder.Default // 갱신 시각
    private LocalDateTime updatedAt = LocalDateTime.now(); // 갱신
}