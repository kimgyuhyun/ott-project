package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Episode {

    @Id // 기본키 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략
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
    private Boolean isActive = true; // 활성화 여부

    @Column(nullable = false)
    private Boolean isReleased = false; // 공개 여부

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩
    @JoinColumn(name = "anime_id", nullable = false) //
    private Anime anime; // 에피소드가 속한 애니 정보

    @OneToMany(mappedBy = "episode", cascade = CascadeType.ALL, orphanRemoval = true) // 일대다 관계, cascade로 연쇄 삭제, 고아 객체 제거
    private List<EpisodeComment> episodeComments = new ArrayList<>(); // 에피소드 댓글 목록

    @CreatedDate // 생성일시 자동 설정
    @Column(nullable = false)
    private LocalDateTime createdAt; // 생성일시

    @LastModifiedDate // 수정일시 자동 업데이트
    @Column(nullable = false)
    private LocalDateTime updatedAt; // 수정일시

    // ===== 정적 팩토리 메서드 =====

    /**
     * 에피소드 생성 (비즈니스 로직 캡슐화)
     * 
     * @param anime 애니메이션 엔티티
     * @param episodeNumber 에피소드 번호 (1 이상)
     * @param title 에피소드 제목 (필수, 공백 불허)
     * @param thumbnailUrl 썸네일 URL (유효한 URL 형식)
     * @param videoUrl 비디오 URL (유효한 URL 형식)
     * @param duration 에피소드 길이 (초 단위)
     * @return 생성된 Episode 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static Episode createEpisode(Anime anime, Integer episodeNumber, String title, 
                                       String thumbnailUrl, String videoUrl, Integer duration) {
        // 필수 필드 검증
        if (anime == null) {
            throw new IllegalArgumentException("애니메이션은 필수입니다.");
        }
        if (episodeNumber == null || episodeNumber < 1) {
            throw new IllegalArgumentException("에피소드 번호는 1 이상이어야 합니다.");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("에피소드 제목은 필수입니다.");
        }
        if (thumbnailUrl == null || thumbnailUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("썸네일 URL은 필수입니다.");
        }
        if (videoUrl == null || videoUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("비디오 URL은 필수입니다.");
        }
        if (duration == null || duration <= 0) {
            throw new IllegalArgumentException("에피소드 길이는 0보다 커야 합니다.");
        }

        // Episode 엔티티 생성
        Episode episode = new Episode();
        episode.anime = anime;
        episode.episodeNumber = episodeNumber;
        episode.title = title.trim();
        episode.thumbnailUrl = thumbnailUrl.trim();
        episode.videoUrl = videoUrl.trim();
        episode.isActive = true;
        episode.isReleased = false;
        episode.episodeComments = new ArrayList<>();

        // 양방향 관계 설정
        if (!anime.getEpisodes().contains(episode)) {
            anime.getEpisodes().add(episode);
        }

        return episode;
    }

    /**
     * 공개된 에피소드 생성 (비즈니스 로직 캡슐화)
     * 
     * @param anime 애니메이션 엔티티
     * @param episodeNumber 에피소드 번호 (1 이상)
     * @param title 에피소드 제목 (필수, 공백 불허)
     * @param thumbnailUrl 썸네일 URL (유효한 URL 형식)
     * @param videoUrl 비디오 URL (유효한 URL 형식)
     * @param duration 에피소드 길이 (초 단위)
     * @param releaseDate 공개일 (현재 시간 이후)
     * @return 생성된 Episode 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static Episode createReleasedEpisode(Anime anime, Integer episodeNumber, String title, 
                                               String thumbnailUrl, String videoUrl, Integer duration, 
                                               LocalDateTime releaseDate) {
        // 공개일 검증
        if (releaseDate == null || releaseDate.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("공개일은 현재 시간 이후여야 합니다.");
        }

        Episode episode = createEpisode(anime, episodeNumber, title, thumbnailUrl, videoUrl, duration);
        episode.isReleased = true;
        return episode;
    }

    /**
     * 초안 에피소드 생성 (비즈니스 로직 캡슐화)
     * 
     * @param anime 애니메이션 엔티티
     * @param episodeNumber 에피소드 번호 (1 이상)
     * @param title 에피소드 제목 (필수, 공백 불허)
     * @return 생성된 Episode 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static Episode createDraftEpisode(Anime anime, Integer episodeNumber, String title) {
        if (anime == null) {
            throw new IllegalArgumentException("애니메이션은 필수입니다.");
        }
        if (episodeNumber == null || episodeNumber < 1) {
            throw new IllegalArgumentException("에피소드 번호는 1 이상이어야 합니다.");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("에피소드 제목은 필수입니다.");
        }

        Episode episode = new Episode();
        episode.anime = anime;
        episode.episodeNumber = episodeNumber;
        episode.title = title.trim();
        episode.thumbnailUrl = ""; // 초안이므로 빈 값
        episode.videoUrl = ""; // 초안이므로 빈 값
        episode.isActive = false; // 초안이므로 비활성
        episode.isReleased = false; // 초안이므로 미공개
        episode.episodeComments = new ArrayList<>();

        // 양방향 관계 설정
        if (!anime.getEpisodes().contains(episode)) {
            anime.getEpisodes().add(episode);
        }

        return episode;
    }

    // ===== 편의 메서드 =====

    /**
     * Anime 설정 메서드
     * @param anime 연결할 Anime 엔티티
     */
    public void setAnime(Anime anime) {
        this.anime = anime;
        // 양방향 관계 설정 
        if (anime != null && !anime.getEpisodes().contains(this)) { 
            anime.getEpisodes().add(this);
        }
    }

    /**
     * 에피소드 댓글 추가 메서드
     * @param episodeComment 추가할 에피소드 댓글
     */
    public void addEpisodeComment(EpisodeComment episodeComment) {
        this.episodeComments.add(episodeComment);
        episodeComment.setEpisode(this); // 양방향 관계 설정
    }

    /**
     * 에피소드 댓글 제거 메서드
     * @param episodeComment 제거할 에피소드 댓글
     */
    public void removeEpisodeComment(EpisodeComment episodeComment) {
        this.episodeComments.remove(episodeComment);
        episodeComment.setEpisode(null); // 양방향 관계 해제
    }
}
