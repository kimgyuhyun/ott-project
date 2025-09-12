package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 리뷰 엔티티
 *
 * 큰 흐름
 * - 작품에 대한 사용자 리뷰(본문/상태)를 저장한다.
 * - 댓글/대댓글/좋아요와 연관되어 커뮤니티 기능의 중심이 된다.
 *
 * 필드 개요
 * - id/content/status: 식별/본문/상태
 * - user/anime: 작성자/대상 작품
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class) // 생성일시 수정일시 자동 관리
public class Review {

    @Id // 기본키 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략
    private Long id; // 리뷰 고유 ID

    @Column(columnDefinition = "TEXT") // 긴 텍스트 저장용
    private String content; // 리뷰 내용

    @Enumerated(EnumType.STRING)
    private ReviewStatus status = ReviewStatus.ACTIVE; // 리뷰 상태 (활성, 삭제됨, 신고됨)

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩
    @JoinColumn(name = "user_id") // 외래키 설정
    private User user; // 리뷰 작성자

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩
    @JoinColumn(name = "ani_id") // 통합 애니 FK
    private Anime anime; // 리뷰가 달린 애니

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt; // 생성일시 (자동 생성)

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt; // 수정일시 (자동 업데이트)

    // ===== 정적 팩토리 메서드 =====

    /**
     * 리뷰 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 리뷰 작성자
     * @param anime 리뷰 대상 애니메이션
     * @param content 리뷰 내용 (10자 이상 1000자 이하)
     * @return 생성된 Review 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static Review createReview(User user, Anime anime, String content) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (anime == null) {
            throw new IllegalArgumentException("애니메이션은 필수입니다.");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("리뷰 내용은 필수입니다.");
        }
        if (content.trim().length() < 10) {
            throw new IllegalArgumentException("리뷰 내용은 10자 이상이어야 합니다.");
        }
        if (content.trim().length() > 1000) {
            throw new IllegalArgumentException("리뷰 내용은 1000자 이하여야 합니다.");
        }

        // Review 엔티티 생성
        Review review = new Review();
        review.user = user;
        review.anime = anime;
        review.content = content.trim();
        review.status = ReviewStatus.ACTIVE;

        return review;
    }

    /**
     * 초안 리뷰 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 리뷰 작성자
     * @param anime 리뷰 대상 애니메이션
     * @return 생성된 Review 엔티티
     * @throws IllegalArgumentException 필수 필드가 null인 경우
     */
    public static Review createDraftReview(User user, Anime anime) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (anime == null) {
            throw new IllegalArgumentException("애니메이션은 필수입니다.");
        }

        // Review 엔티티 생성
        Review review = new Review();
        review.user = user;
        review.anime = anime;
        review.content = ""; // 초안이므로 빈 내용
        review.status = ReviewStatus.ACTIVE;

        return review;
    }

    /**
     * 신고된 리뷰 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 리뷰 작성자
     * @param anime 리뷰 대상 애니메이션
     * @param content 리뷰 내용
     * @param reason 신고 사유
     * @return 생성된 Review 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static Review createReportedReview(User user, Anime anime, String content, String reason) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (anime == null) {
            throw new IllegalArgumentException("애니메이션은 필수입니다.");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("리뷰 내용은 필수입니다.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("신고 사유는 필수입니다.");
        }

        // Review 엔티티 생성
        Review review = new Review();
        review.user = user;
        review.anime = anime;
        review.content = content.trim();
        review.status = ReviewStatus.REPORTED;

        return review;
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 리뷰 내용 수정
     * @param newContent 새로운 리뷰 내용
     * @throws IllegalStateException 수정할 수 없는 상태인 경우
     * @throws IllegalArgumentException 내용이 유효하지 않은 경우
     */
    public void updateContent(String newContent) {
        if (this.status == ReviewStatus.DELETED) {
            throw new IllegalStateException("삭제된 리뷰는 수정할 수 없습니다.");
        }
        if (newContent == null || newContent.trim().isEmpty()) {
            throw new IllegalArgumentException("리뷰 내용은 필수입니다.");
        }
        if (newContent.trim().length() < 10) {
            throw new IllegalArgumentException("리뷰 내용은 10자 이상이어야 합니다.");
        }
        if (newContent.trim().length() > 1000) {
            throw new IllegalArgumentException("리뷰 내용은 1000자 이하여야 합니다.");
        }

        this.content = newContent.trim();
    }

    /**
     * 리뷰 삭제
     * @throws IllegalStateException 이미 삭제된 리뷰인 경우
     */
    public void delete() {
        if (this.status == ReviewStatus.DELETED) {
            throw new IllegalStateException("이미 삭제된 리뷰입니다.");
        }

        this.status = ReviewStatus.DELETED;
    }

    /**
     * 리뷰 신고
     * @param reason 신고 사유
     * @throws IllegalStateException 신고할 수 없는 상태인 경우
     * @throws IllegalArgumentException 신고 사유가 유효하지 않은 경우
     */
    public void report(String reason) {
        if (this.status == ReviewStatus.DELETED) {
            throw new IllegalStateException("삭제된 리뷰는 신고할 수 없습니다.");
        }
        if (this.status == ReviewStatus.REPORTED) {
            throw new IllegalStateException("이미 신고된 리뷰입니다.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("신고 사유는 필수입니다.");
        }

        this.status = ReviewStatus.REPORTED;
    }

    /**
     * 리뷰 활성화 (관리자 승인 후)
     * @throws IllegalStateException 활성화할 수 없는 상태인 경우
     */
    public void activate() {
        if (this.status != ReviewStatus.REPORTED) {
            throw new IllegalStateException("신고된 리뷰만 활성화할 수 있습니다.");
        }

        this.status = ReviewStatus.ACTIVE;
    }
}
