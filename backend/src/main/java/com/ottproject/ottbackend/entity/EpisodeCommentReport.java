package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 에피소드 댓글 신고 기록 엔티티
 *
 * 큰 흐름
 * - "누가 어떤 댓글을 신고했는지"를 1건씩 저장한다.
 * - (episode_comment_id, user_id) 유니크로 사용자당 1회만 허용 → 단독 신고로 즉시 숨김 방지.
 * - 신고 수가 임계치를 넘으면 서비스가 댓글 상태를 REPORTED로 전환한다.
 */
@Entity
@Table(name = "episode_comment_reports",
        uniqueConstraints = @UniqueConstraint(columnNames = {"episode_comment_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EpisodeCommentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_comment_id", nullable = false)
    private EpisodeComment episodeComment; // 신고 대상 댓글

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 신고자

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static EpisodeCommentReport create(EpisodeComment episodeComment, User user) {
        EpisodeCommentReport report = new EpisodeCommentReport();
        report.episodeComment = episodeComment;
        report.user = user;
        return report;
    }
}
