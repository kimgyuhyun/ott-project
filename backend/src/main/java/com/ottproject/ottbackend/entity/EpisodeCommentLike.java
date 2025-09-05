package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 에피소드 댓글 좋아요 엔티티
 *
 * 큰 흐름
 * - 사용자와 에피소드 댓글 간의 좋아요 관계를 보관한다.
 * - (user, episode_comment) 복합 유니크로 중복 좋아요를 방지한다.
 *
 * 필드 개요
 * - id/createdAt: 식별/생성 시각
 * - user/episodeComment: 소유자/대상 에피소드 댓글
 */
@Entity
@Table( // 테이블 매핑
        name = "episode_comment_likes", // 테이블명
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","episode_comment_id"}) // 복합 유니크
) // 유니크로 동일 사용자 중복 방지
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EpisodeCommentLike  {

    @Id // 기본키 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략
    private Long id; // 에피소드 댓글 좋아요 고유 ID

    @CreatedDate // 생성 시간 자동 설정
    @Column(nullable = false)
    private LocalDateTime createdAt; // 좋아요 누른 시간

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩으로 성능 최적화
    @JoinColumn(name = "user_id", nullable = false) // 외래키 설정
    private User user; // 좋아요를 누른 사용자 (다대일 관계 - 한 사용자가 여러 좋아요를 누를 수 있음)

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩으로 성능 최적화
    @JoinColumn(name = "episode_comment_id", nullable = false) // 외래키 설정
    private EpisodeComment episodeComment; // 좋아요가 달린 에피소드 댓글 (다대일 관계 - 여러 좋아요 레코드가 하나의 에피소드 댓글을 참조)

    // ===== 정적 팩토리 메서드 =====

    /**
     * 에피소드 댓글 좋아요 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 좋아요를 누른 사용자
     * @param episodeComment 좋아요 대상 에피소드 댓글
     * @return 생성된 EpisodeCommentLike 엔티티
     * @throws IllegalArgumentException 필수 필드가 null인 경우
     */
    public static EpisodeCommentLike createLike(User user, EpisodeComment episodeComment) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (episodeComment == null) {
            throw new IllegalArgumentException("에피소드 댓글은 필수입니다.");
        }

        // EpisodeCommentLike 엔티티 생성
        EpisodeCommentLike like = new EpisodeCommentLike();
        like.user = user;
        like.episodeComment = episodeComment;

        return like;
    }
}
