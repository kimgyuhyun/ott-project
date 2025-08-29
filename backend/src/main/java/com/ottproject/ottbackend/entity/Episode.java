package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 에피소드 엔티티
 *
 * 큰 흐름
 * - 작품의 회차 정보를 저장한다(제목/썸네일/영상/공개/활성 등).
 * - Anime 과 다대일 연관을 맺고 Auditing 으로 시각을 관리한다.
 *
 * 필드 개요
 * - id/episodeNumber/title: 식별/회차/제목
 * - thumbnailUrl/videoUrl: 미디어 리소스 URL
 * - isActive/isReleased: 운영/공개 플래그
 * - anime: 소속 작품
 * - createdAt/updatedAt: 생성/수정 시각
 */
@Entity
@Table(name = "episodes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Episode {

    @Id // 기본키 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동ㅈ ㅡㅇ가 전략
    private Long id; // 에피소드 고유 ID

    @Column(nullable = false) // null 불허
    private Integer episodeNumber; // 에피소드 번호 (1화, 2화, 3화)

    @Column(nullable = false)
    private String title; // 에피소드 제목

    @Column(name = "thumbnail_url", nullable = false)
    private String thumbnailUrl; // 에피소드 썸네일 이미지

    @Column(name = "video_url", nullable = false)
    private String videoUrl; // 에피소드 영상 URL

    @Column(nullable = false)
    private Boolean isActive; // 활성화 여부

    @Column(nullable = false)
    private Boolean isReleased; // 공개 여부

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩
    @JoinColumn(name = "anime_id", nullable = false) //
    private Anime anime; // NEW 에피소드가 속한 애니 정보

    @CreatedDate // 생성일시 자동 설정
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(); // 생성일시

    @LastModifiedDate // 수정일시 자동 업데이트
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now(); // 수정일시

    // ===== 편의 메서드 =====

    /**
     * Anime 설정 메서드 // NEW
     * @param anime 연결할 Anime 엔티티
     */
    public void setAnime(Anime anime) {
        this.anime = anime;
        // 양방향 관계 설정 
        if (anime != null && !anime.getEpisodes().contains(this)) { 
            anime.getEpisodes().add(this);
        }
    }
}
