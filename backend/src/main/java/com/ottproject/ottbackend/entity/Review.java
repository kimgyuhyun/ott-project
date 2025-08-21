package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 리뷰 엔티티
 *
 * 큰 흐름
 * - 작품에 대한 사용자 리뷰(본문/평점/상태)를 저장한다.
 * - 댓글/대댓글/좋아요와 연관되어 커뮤니티 기능의 중심이 된다.
 *
 * 필드 개요
 * - id/content/rating/status: 식별/본문/평점/상태
 * - user/anime: 작성자/대상 작품
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class) // 생성일시 수정일시 자동 관리
public class Review {

    @Id // 기본키 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략
    private Long id; // 리뷰 고유 ID

    @Column(columnDefinition = "TEXT") // 긴 텍스트 저장용
    private String content; // 리뷰 내용 (null 가능 - 평점만 달 수도 있음)

    @Column(columnDefinition = "NUMERIC(2,1)") // Postgres: 0.5~5.0 한 자리 소수
    private Double rating; // 평점(1~5, null 가능 - 댓글만 달 수도 있음

    @Enumerated(EnumType.STRING)
    private ReviewStatus status; // 리뷰 상태 (활성, 삭제됨, 신고됨)

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩
    @JoinColumn(name = "user_id") // 외래키 설정
    private User user; // 리뷰 작성자

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩
    @JoinColumn(name = "ani_id") // 통합 애니 FK
    private Anime anime; // 리뷰가 달린 애니
}
