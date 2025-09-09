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
    private LocalDateTime createdAt; // 등록 일시

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt; // 수정 일시

    // ===== 정적 팩토리 메서드 =====

    /**
     * 평점 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 평점 작성자
     * @param anime 평점 대상 애니메이션
     * @param score 평점 (1-10)
     * @return 생성된 Rating 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static Rating createRating(User user, Anime anime, Double score) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (anime == null) {
            throw new IllegalArgumentException("애니메이션은 필수입니다.");
        }
        if (score == null) {
            throw new IllegalArgumentException("평점은 필수입니다.");
        }
        if (score < 0.0 || score > 5.0) {
            throw new IllegalArgumentException("평점은 0-5 범위 내여야 합니다.");
        }

        // Rating 엔티티 생성
        Rating rating = new Rating();
        rating.user = user;
        rating.anime = anime;
        rating.score = score;

        return rating;
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 평점 수정 (비즈니스 로직 캡슐화)
     * 
     * @param user 사용자
     * @param anime 애니메이션
     * @param score 새로운 평점 (1-10)
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public void updateRating(User user, Anime anime, Double score) {
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (anime == null) {
            throw new IllegalArgumentException("애니메이션은 필수입니다.");
        }
        if (score == null) {
            throw new IllegalArgumentException("평점은 필수입니다.");
        }
        if (score < 1.0 || score > 5.0) {
            throw new IllegalArgumentException("평점은 1-5 범위 내여야 합니다.");
        }

        this.score = score;
    }
}
