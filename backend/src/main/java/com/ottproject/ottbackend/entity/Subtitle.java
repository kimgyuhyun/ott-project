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

    // ===== 정적 팩토리 메서드 =====

    /**
     * 자막 생성 (비즈니스 로직 캡슐화)
     * 
     * @param episode 에피소드
     * @param language 언어 코드
     * @param content 자막 내용
     * @param startTime 시작 시간 (초)
     * @param endTime 종료 시간 (초)
     * @return 생성된 Subtitle 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static Subtitle createSubtitle(Episode episode, String language, String content, Integer startTime, Integer endTime) {
        // 필수 필드 검증
        if (episode == null) {
            throw new IllegalArgumentException("에피소드는 필수입니다.");
        }
        if (language == null || language.trim().isEmpty()) {
            throw new IllegalArgumentException("언어 코드는 필수입니다.");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("자막 내용은 필수입니다.");
        }
        if (startTime == null || startTime < 0) {
            throw new IllegalArgumentException("시작 시간은 0 이상이어야 합니다.");
        }
        if (endTime == null || endTime <= startTime) {
            throw new IllegalArgumentException("종료 시간은 시작 시간보다 커야 합니다.");
        }

        // Subtitle 엔티티 생성
        Subtitle subtitle = new Subtitle();
        subtitle.episode = episode;
        subtitle.language = language.trim();
        subtitle.url = ""; // 기본값, 나중에 설정
        subtitle.isDefault = false; // 기본값

        return subtitle;
    }
}
