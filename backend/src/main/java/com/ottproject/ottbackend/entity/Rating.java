package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 평점 엔티티
 *
 * 큰 흐름
 * - 사용자→작품 평점 점수를 보관한다.
 * - 리뷰와 분리하여 단독 평점만 남길 수 있도록 한다.
 *
 * 필드 개요
 * - id/score: 식별/점수
 * - user/anime: 작성자/대상 작품
 * - createdAt/updatedAt: 생성/수정 시각
 */
@Entity
@Table(name = "ratings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략
    private Long id;

    @Column(nullable = false)
    private Double score; // 평점(0.0 ~ 5.0)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // 평점을 남긴 사용자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ani_id") // 통합 애니 FK
    private Anime anime; // 평점이 달린 애니

    
    @CreatedDate
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(); // 등록 일시

    @LastModifiedDate
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now(); // 수정 일시
}
