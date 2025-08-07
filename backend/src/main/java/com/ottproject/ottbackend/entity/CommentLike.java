package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 댓글 좋아요를 저장하는 엔티티
 * 사용자와 댓글 간의 좋아요 관계를 관리하며, 중복 좋아요를 방지합니다.
 */
@Entity
@Table(name = "comment_likes")
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

    // 복합 유니크 제약 조건 (한 사용자가 한 댓글에 좋아요를 한 번만 누를 수 있음)
    @Table(uniqueConstraints = {
            @UniqueConstraint(columnNames = {"user_id", "comment_id"})
    })
    public static class CommentLikeTable {}
}
