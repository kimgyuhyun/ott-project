package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 댓글 좋아요 엔티티
 *
 * 큰 흐름
 * - 사용자와 댓글 간의 좋아요 관계를 보관한다.
 * - (user, comment) 복합 유니크로 중복 좋아요를 방지한다.
 *
 * 필드 개요
 * - id/createdAt: 식별/생성 시각
 * - user/comment: 소유자/대상 댓글
 */
@Entity
@Table( // 테이블 매핑
        name = "comment_likes", // 테이블명
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","comment_id"}) // 복합 유니크
) // 유니크로 동일 사용자 중복 방지
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CommentLike  {

    @Id // 기본키 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략
    private Long id; // 댓글 좋아요 고유 ID

    @CreatedDate // 생성 시간 자동 설정
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(); // 좋아요 누른 시간

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩으로 성능 최적화
    @JoinColumn(name = "user_id", nullable = false) // 외래키 설정
    private User user; // 좋아요를 누른 사용자 (다대일 관계 - 한 사용자가 여러 좋아요를 누를 수 있음)

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩으로 성능 최적화
    @JoinColumn(name = "comment_id", nullable = false) // 외래키 설정
    private Comment comment; // 좋아요가 달린 댓글 (다대일 관계 - 여러 좋아요 레코드가 하나의 댓글을 참조)
}
