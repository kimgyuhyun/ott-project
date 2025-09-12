package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.CommentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 에피소드 댓글 엔티티
 *
 * 큰 흐름
 * - 에피소드에 달리는 댓글/대댓글을 저장한다.
 * - parent 필드로 1레벨 대댓글을 구성하고, 플랫 렌더링을 지원한다.
 *
 * 필드 개요
 * - id/content/status: 식별/본문/상태
 * - user/episode: 작성자/대상 에피소드
 * - parent/replies: 대댓글 구조
 */
@Entity
@Table(name = "episode_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EpisodeComment {

    @Id // 기본키 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략
    private Long id; // 에피소드 댓글 고유 ID

    @Column(columnDefinition = "TEXT") // 긴 텍스트 저장용
    private String content; // 댓글 내용

    @Enumerated(EnumType.STRING) // enum 을 문자열로 저장
    private CommentStatus status = CommentStatus.ACTIVE; // 댓글 상태 (활성, 삭제됨, 신고됨)

    @ManyToOne(fetch = FetchType.LAZY)  // 다대일 관계 ,지연 로딩
    @JoinColumn(name = "user_id") // 외래키 설정
    private User user; // 댓글 작성자

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩
    @JoinColumn(name = "episode_id", nullable = false) // 외래키 설정
    private Episode episode; // 댓글이 달린 에피소드

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩
    @JoinColumn(name = "parent_id") // 외래키 설정
    private EpisodeComment parent; // 부모 댓글(대댓글 구조)

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true) // 일대다 관계, cascade 로 연쇄 삭제, 고아 객체 제거
    private List<EpisodeComment> replies = new ArrayList<>(); // 대댓글 목록

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt; // 생성일시 (자동 생성)

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt; // 수정일시 (자동 업데이트)

    // ===== 정적 팩토리 메서드 =====

    /**
     * 에피소드 댓글 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 댓글 작성자
     * @param episode 댓글 대상 에피소드
     * @param content 댓글 내용 (1-500자)
     * @return 생성된 EpisodeComment 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static EpisodeComment createComment(User user, Episode episode, String content) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (episode == null) {
            throw new IllegalArgumentException("에피소드는 필수입니다.");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("댓글 내용은 필수입니다.");
        }
        if (content.trim().length() < 1) {
            throw new IllegalArgumentException("댓글 내용은 1자 이상이어야 합니다.");
        }
        if (content.trim().length() > 500) {
            throw new IllegalArgumentException("댓글 내용은 500자 이하여야 합니다.");
        }

        // EpisodeComment 엔티티 생성
        EpisodeComment comment = new EpisodeComment();
        comment.user = user;
        comment.episode = episode;
        comment.content = content.trim();
        comment.status = CommentStatus.ACTIVE;
        comment.replies = new ArrayList<>();

        return comment;
    }

    /**
     * 에피소드 대댓글 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 댓글 작성자
     * @param episode 댓글 대상 에피소드
     * @param parentComment 부모 댓글
     * @param content 댓글 내용 (1-500자)
     * @return 생성된 EpisodeComment 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static EpisodeComment createReply(User user, Episode episode, EpisodeComment parentComment, String content) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (episode == null) {
            throw new IllegalArgumentException("에피소드는 필수입니다.");
        }
        if (parentComment == null) {
            throw new IllegalArgumentException("부모 댓글은 필수입니다.");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("댓글 내용은 필수입니다.");
        }
        if (content.trim().length() < 1) {
            throw new IllegalArgumentException("댓글 내용은 1자 이상이어야 합니다.");
        }
        if (content.trim().length() > 500) {
            throw new IllegalArgumentException("댓글 내용은 500자 이하여야 합니다.");
        }

        // 대댓글 깊이 검증 (3단계까지만 허용)
        if (parentComment.parent != null) {
            throw new IllegalArgumentException("대댓글은 3단계까지만 허용됩니다.");
        }

        // EpisodeComment 엔티티 생성
        EpisodeComment comment = new EpisodeComment();
        comment.user = user;
        comment.episode = episode;
        comment.parent = parentComment;
        comment.content = content.trim();
        comment.status = CommentStatus.ACTIVE;
        comment.replies = new ArrayList<>();

        // 양방향 관계 설정
        parentComment.addReply(comment);

        return comment;
    }
    
    // ===== 편의 메서드 =====

    /**
     * 대댓글인지 확인
     * @return 부모 댓글이 있으면 true (대댓글), 없으면 false (최상위 댓글)
     */
    public boolean isReply() {
        return parent != null; // 부모가 있으면 대댓글
    }


    /**
     * 대댓글 추가 메서드
     * @param reply 추가할 대댓글
     */
    public void addReply(EpisodeComment reply) {
        this.replies.add(reply);
        reply.setParent(this); // 양방향 관계 설정
    }

    /**
     * 대댓글 제거 메서드
     * @param reply 제거할 대댓글
     */
    public void removeReply(EpisodeComment reply) {
        this.replies.remove(reply);
        reply.setParent(null); // 양방향 관계 해제
    }
}
