package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 애니메이션 상세 정보를 저장하는 엔티티
 * 라프텔의 "더보기" 팝업에서 보여지는 정보들
 * AniList 와 일대일 관계로 연결
 */
@Entity
@Table(name = "ani_detail")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AniDetail {

    @Id // 기본키 설정
    @GeneratedValue(strategy = GenerationType.AUTO) // 자동 증가 전략
    private Long id; // 상세 정보 고유 ID

    // ===== AniList 와의 관계 =====

    /**
     * AniList 와의 일대일 관계
     * 하나의 AniList 는 하나의 AniDetail 을 가짐
     * 외래키를 AniDetail 에서 관리 (주인)
     */
    @OneToOne(fetch = FetchType.LAZY) // 일대일 관계, 지연 로딩
    @JoinColumn(name = "ani_list_id", nullable = false, unique = true) // 외래키 설정, null 불허, 고유값
    private AniList aniList; // 연결된 AniList 엔티티

    // ===== 상세 정보 필드 ======
    @Column(columnDefinition = "TEXT") // 긴 텍스트 저장용
    private String fullSynopsis; // 전체 줄거리 (더보기에서 보여지는 전체 내용)

    @Column(columnDefinition = "TEXT") // 긴 텍스트 저장용
    private String tags; // 태그 정보 (JSON 배열 형태: ["#가족", "#감동"])

    // ===== 성우 정보 =====
    @Column(columnDefinition = "TEXT") // 긴 텍스트 저장용
    private String voiceActors; // 성우 정보 (JSON 형태: [{"character": "짱구", "actor": "박영남"}, ...])

    // ===== 제작 정보 =====

    @Column(nullable = true)
    private String director; // 감독

    @Column(nullable = true)
    private String releaseQuarter; // 출시 분기 (예: 2019년 3분기)

    // ===== 상세 페이지 추가 정보 =====
    @Column(nullable = false)
    private Boolean isCompleted; // 완결 여부

    @Column(nullable = false)
    private Boolean isPopular; // 인기작 여부

    @Column(nullable = false)
    private Boolean isExclusive; // 라프텔 독점 여부
    // ===== 에피소드 관련 =====
    /**
     * 에피소드와의 일대다 관계
     * 하나의 AniDetail 은 여러 에피소드를 가짐
     */
    @OneToMany(mappedBy = "aniDetail", cascade = CascadeType.ALL, fetch = FetchType.LAZY) // 일대다 관계, cascade 로 연쇄 삭제, 지연 로딩
    @Builder.Default
    private java.util.List<Episode> episodes = new java.util.ArrayList<>(); // 에피소드 목록

    @Column(nullable = false)
    private Integer currentEpisodes; // 현재 업로드된 에피소드 수

    // ===== 관리 정보 =====
    @Column(nullable = false)
    private Boolean isActive; // 활성화 여부

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
     * AniList 설정 메서드
     * @param aniList 연결할 AniList 엔티티
     */
    public void setAniList(AniList aniList) {
        this.aniList = aniList;
        // 양방향 관계 설정
        if (aniList != null && aniList.getAniDetail() != this) {
            aniList.setAniDetail(this);
        }
    }

    /**
     * 장르 정보 가져오기
     * @return 장르 목록
     */
    public java.util.Set<Genre> getGenres() {
        return this.aniList != null ? this.aniList.getGenres() : new java.util.HashSet<>();
    }

    /**
     * 제작사 정보 가져오기
     * @return 제작사 목록
     */
    public java.util.Set<Studio> getStudios() {
        return this.aniList != null ? this.aniList.getStudios() : new java.util.HashSet<>();
    }

    /**
     * 에피소드 추가 메서드
     * @param episode 추가할 에피소드
     */
    public void addEpisode(Episode episode) {
        this.episodes.add(episode);
        episode.setAniDetail(this); // 양방향 관계 설정
    }

    /**
     * 에피소드 제거 메서드
     * @param episode 제거할 에피소드
     */
    public void removeEpisode(Episode episode) {
        this.episodes.remove(episode);
        episode.setAniDetail(null); // 양방향 관계 해제
    }

}
