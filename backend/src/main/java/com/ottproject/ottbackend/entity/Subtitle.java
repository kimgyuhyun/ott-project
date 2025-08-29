package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 자막 엔티티
 *
 * 큰 흐름
 * - 에피소드별 자막 파일 정보를 저장한다.
 * - 다국어 자막 지원을 위한 언어별 관리
 *
 * 필드 개요
 * - id: 자막 ID
 * - episode: 소속 에피소드
 * - language: 언어 코드
 * - url: 웹VTT 파일 URL
 * - isDefault: 기본 자막 여부
 */
@Entity
@Table(name = "subtitles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Subtitle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @Column(nullable = false)
    private String language; // 언어 코드 (ko, en, ja)

    @Column(name = "url", nullable = false)
    private String url; // 웹VTT 파일 URL

    @Column(name = "is_default", nullable = false)
    private boolean isDefault; // 기본 자막 여부

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
