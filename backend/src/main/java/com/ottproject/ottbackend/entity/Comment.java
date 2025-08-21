package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.CommentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.ArrayList;
import java.util.List;

/**
 * 댓글 엔티티
 *
 * 큰 흐름
 * - 리뷰에 달리는 댓글/대댓글을 저장한다.
 * - parent 필드로 1레벨 대댓글을 구성하고, 플랫 렌더링을 지원한다.
 *
 * 필드 개요
 * - id/content/status: 식별/본문/상태
 * - user/review: 작성자/대상 리뷰
 * - parent/replies: 대댓글 구조
 */
@Entity
@Table(name = "comments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Comment {

    @Id // 기본키 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략
    private Long id; // 댓글 고유 ID

    @Column(columnDefinition = "TEXT") // 긴 텍스트 저장용
    private String content; // 댓글 내용

    @Enumerated(EnumType.STRING) // enum 을 문자열로 저장
    private CommentStatus status; // 댓글 상태 (활성, 삭제됨, 신고됨)

    @ManyToOne(fetch = FetchType.LAZY)  // 다대일 관계 ,지연 로딩
    @JoinColumn(name = "user_id") // 외래키 설정
    private User user; // 댓글 작성자

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩
    private Review review; // 댓글이 달린 리뷰

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩
    @JoinColumn(name = "parent_id") // 외래키 설정
    private Comment parent; // 부모 댓글(대댓글 구조)

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true) // 일대다 관계, cascade 로 연쇄 삭제, 고아 객체 제거
    @Builder.Default
    private List<Comment> replies = new ArrayList<>(); // 대댓글 목록
    
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
    public void addReply(Comment reply) {
        this.replies.add(reply);
        reply.setParent(this); // 양방향 관계 설정
    }

    /**
     * 대댓글 제거 메서드
     * @param reply 제거할 대댓글
     */
    public void removeReply(Comment reply) {
        this.replies.remove(reply);
        reply.setParent(null); // 양방향 관계 해제
    }
}
