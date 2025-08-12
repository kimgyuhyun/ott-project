package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 애니메이션 에피소드 정보를 저장하는 엔티티
 * 라픝레의 에피소드 구조 기본 설계
 */
@Entity
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

    @Column(nullable = false)
    private String thumbnailUrl; // 에피소드 썸네일 이미지

    @Column(nullable = false)
    private String videoUrl; // 에피소드 영상 URL

    @Column(nullable = false)
    private Boolean isActive; // 활성화 여부

    @Column(nullable = false)
    private Boolean isReleased; // 공개 여부

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩
    private AniDetail aniDetail; // 에피소드가 속한 애니 상세 정보

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
     * AniDetail 설정 메서드
     * @param aniDetail 연결할 AniDetail 엔티티
     */
    public void setAniDetail(AniDetail aniDetail) {
        this.aniDetail = aniDetail;
        // 양방향 관계 설정
        if (aniDetail != null && !aniDetail.getEpisodes().contains(this)) {
            aniDetail.getEpisodes().add(this);
        }
    }
}
