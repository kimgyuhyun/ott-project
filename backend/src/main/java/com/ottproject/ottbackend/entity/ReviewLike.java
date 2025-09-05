package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 리뷰 좋아요 엔티티
 *
 * 큰 흐름
 * - 사용자와 리뷰 간의 좋아요 관계를 보관한다.
 * - (user, review) 복합 유니크로 중복 좋아요를 방지한다.
 *
 * 필드 개요
 * - id/createdAt: 식별/생성 시각
 * - user/review: 소유자/대상 리뷰
 */
@Entity
@Table( // 테이블 매핑
        name = "review_likes", // 테이블명
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","review_id"}) // 복합 유니크(중복 방지)
) // 엔티티 레벨에서 유니크 선언(중요)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class) // 생성일시 자동 주입
public class ReviewLike {
    @Id // 기본키 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략
    private Long id; // 리뷰 좋아요 고유 ID

    @CreatedDate // 생성 시간 자동 설정
    @Column(nullable = false)
    private LocalDateTime createdAt; // 좋아요 누른 시간

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩
    @JoinColumn(name = "user_id", nullable = false) // 외래키 설정
    private User user; // 좋아요를 누른 사용자 (다대일 관계 - 한 사용자가 여러 좋아요를 누를 수 있음)

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩
    @JoinColumn(name = "review_id", nullable = false) // 외래키 설정
    private Review review; // 좋아요가 달린 리뷰 (다대일 관계 - 여러 좋아요 레코드가 하나의 리뷰를 참조)

    // ===== 정적 팩토리 메서드 =====

    /**
     * 리뷰 좋아요 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 좋아요를 누른 사용자
     * @param review 좋아요 대상 리뷰
     * @return 생성된 ReviewLike 엔티티
     * @throws IllegalArgumentException 필수 필드가 null인 경우
     */
    public static ReviewLike createLike(User user, Review review) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (review == null) {
            throw new IllegalArgumentException("리뷰는 필수입니다.");
        }

        // ReviewLike 엔티티 생성
        ReviewLike like = new ReviewLike();
        like.user = user;
        like.review = review;

        return like;
    }
}
