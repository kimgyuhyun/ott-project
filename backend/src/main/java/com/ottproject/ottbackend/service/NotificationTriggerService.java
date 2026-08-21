package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.AnimeFavorite;
import com.ottproject.ottbackend.entity.Comment;
import com.ottproject.ottbackend.entity.CommentLike;
import com.ottproject.ottbackend.entity.Episode;
import com.ottproject.ottbackend.entity.EpisodeComment;
import com.ottproject.ottbackend.entity.EpisodeCommentLike;
import com.ottproject.ottbackend.repository.AnimeFavoriteRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * NotificationTriggerService
 *
 * 큰 흐름
 * - 각종 이벤트 발생 시 알림 생성을 트리거하는 서비스
 * - 비즈니스 로직과 알림 로직을 분리하여 관리
 *
 * 메서드 개요
 * - triggerEpisodeUpdateNotification: 에피소드 업데이트 시 알림 생성
 * - triggerCommentLikeNotification: 댓글 좋아요 시 알림 생성
 * - triggerCommentReplyNotification: 댓글 작성 시 알림 생성
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationTriggerService {

    private final NotificationService notificationService;
    private final AnimeFavoriteRepository animeFavoriteRepository;

    /**
     * 에피소드 업데이트 시 알림 생성
     *
     * @param episode 새로 생성된 에피소드
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void triggerEpisodeUpdateNotification(Episode episode) {
        try {
            Long animeId = episode.getAnime().getId();
            String animeTitle = episode.getAnime().getTitle();
            int episodeNumber = episode.getEpisodeNumber();
            Long episodeId = episode.getId();

            // 해당 애니메이션을 찜한 사용자들 조회
            List<AnimeFavorite> favorites = animeFavoriteRepository.findByAnimeId(animeId);

            log.info("애니메이션 {} 업데이트 - 찜한 사용자 {}명에게 알림 발송", animeTitle, favorites.size());

            // 각 사용자에게 알림 생성
            for (AnimeFavorite favorite : favorites) {
                Long userId = favorite.getUser().getId();
                notificationService.createEpisodeUpdateNotification(
                        userId, animeTitle, episodeNumber, animeId, episodeId);
            }

        } catch (Exception e) {
            log.error("에피소드 업데이트 알림 생성 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 댓글 좋아요 시 알림 생성
     *
     * @param like 새로 생성된 좋아요
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void triggerCommentLikeNotification(CommentLike like) {
        try {
            log.info("🔔 [TRIGGER] 댓글 좋아요 알림 트리거 시작 - Like ID: {}", like.getId());

            // 리뷰 댓글인지 에피소드 댓글인지 확인
            if (like.getComment() != null) {
                Long userId = like.getComment().getUser().getId();
                Long actorId = like.getUser().getId();

                log.info("🔔 [TRIGGER] 댓글 작성자: {}, 좋아요 누른 사용자: {}", userId, actorId);

                // 자신의 댓글에 좋아요를 누른 경우 알림 생성하지 않음
                if (userId.equals(actorId)) {
                    log.info("🔔 [TRIGGER] 자신의 댓글에 좋아요를 누른 경우 알림 생성하지 않음: 사용자 {}", userId);
                    return;
                }

                String actorName = like.getUser().getName();
                Long contentId = like.getComment().getId();
                Long animeId = like.getComment().getReview().getAnime().getId();

                log.info(
                        "🔔 [TRIGGER] 알림 생성 호출 - 대상: {}, 활동자: {}, 댓글: {}, 애니메이션: {}",
                        userId,
                        actorName,
                        contentId,
                        animeId);

                notificationService.createCommentActivityNotification(
                        userId, actorName, "COMMENT_LIKE", "REVIEW_COMMENT", contentId, animeId, null, null);

                log.info("🔔 [TRIGGER] 리뷰 댓글 좋아요 알림 생성 완료: 사용자 {} -> 댓글 작성자 {}", actorName, userId);
            } else {
                log.warn("🔔 [TRIGGER] CommentLike에 Comment가 null입니다.");
            }

        } catch (Exception e) {
            log.error("🔔 [TRIGGER] 댓글 좋아요 알림 생성 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 에피소드 댓글 좋아요 시 알림 생성
     *
     * @param like 새로 생성된 좋아요
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void triggerEpisodeCommentLikeNotification(EpisodeCommentLike like) {
        try {
            EpisodeComment comment = like.getEpisodeComment();
            if (comment == null) return;

            Long userId = comment.getUser().getId();
            Long actorId = like.getUser().getId();

            // 자신의 댓글에 좋아요를 누른 경우 알림 생성하지 않음
            if (userId.equals(actorId)) {
                log.debug("자신의 에피소드 댓글에 좋아요를 누른 경우 알림 생성하지 않음: 사용자 {}", userId);
                return;
            }

            String actorName = like.getUser().getName();
            Long contentId = comment.getId();
            Long animeId = comment.getEpisode().getAnime().getId();
            Long episodeId = comment.getEpisode().getId();

            notificationService.createCommentActivityNotification(
                    userId, actorName, "COMMENT_LIKE", "EPISODE_COMMENT", contentId, animeId, episodeId, null);

            log.info("에피소드 댓글 좋아요 알림 생성: 사용자 {} -> 댓글 작성자 {}", actorName, userId);

        } catch (Exception e) {
            log.error("에피소드 댓글 좋아요 알림 생성 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 리뷰 댓글 작성 시 알림 생성
     *
     * @param comment 새로 작성된 댓글
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void triggerReviewCommentNotification(Comment comment) {
        try {
            log.info("🔔 [TRIGGER] 리뷰 댓글 알림 트리거 시작 - Comment ID: {}", comment.getId());

            Long actorId = comment.getUser().getId();
            String actorName = comment.getUser().getName();
            Long animeId = comment.getReview().getAnime().getId();

            log.info("🔔 [TRIGGER] 댓글 작성자: {}, 애니메이션: {}", actorId, animeId);

            // 1. 대댓글인 경우: 원댓글 작성자에게 알림
            if (comment.getParent() != null) {
                Long parentCommentUserId = comment.getParent().getUser().getId();

                log.info("🔔 [TRIGGER] 대댓글 감지 - 원댓글 작성자: {}, 현재 작성자: {}", parentCommentUserId, actorId);

                // 자신의 댓글에 대댓글을 단 경우 알림 생성하지 않음
                if (!parentCommentUserId.equals(actorId)) {
                    Long contentId = comment.getParent().getId();

                    log.info(
                            "🔔 [TRIGGER] 대댓글 알림 생성 호출 - 대상: {}, 활동자: {}, 댓글: {}",
                            parentCommentUserId,
                            actorName,
                            contentId);

                    notificationService.createCommentActivityNotification(
                            parentCommentUserId,
                            actorName,
                            "COMMENT_REPLY",
                            "REVIEW_COMMENT",
                            contentId,
                            animeId,
                            null,
                            comment.getContent());

                    log.info("🔔 [TRIGGER] 리뷰 대댓글 알림 생성 완료: 사용자 {} -> 댓글 작성자 {}", actorName, parentCommentUserId);
                } else {
                    log.info("🔔 [TRIGGER] 자신의 댓글에 대댓글을 단 경우 알림 생성하지 않음: 사용자 {}", actorId);
                }
            } else {
                log.info("🔔 [TRIGGER] 일반 댓글 감지");
            }

            // 2. 리뷰 작성자에게 알림 (자신의 리뷰가 아닌 경우만)
            Long reviewUserId = comment.getReview().getUser().getId();

            log.info("🔔 [TRIGGER] 리뷰 작성자: {}, 댓글 작성자: {}", reviewUserId, actorId);

            if (!reviewUserId.equals(actorId)) {
                Long contentId = comment.getId();

                log.info("🔔 [TRIGGER] 리뷰 댓글 알림 생성 호출 - 대상: {}, 활동자: {}, 댓글: {}", reviewUserId, actorName, contentId);

                notificationService.createCommentActivityNotification(
                        reviewUserId,
                        actorName,
                        "COMMENT_REPLY",
                        "REVIEW_COMMENT",
                        contentId,
                        animeId,
                        null,
                        comment.getContent());

                log.info("🔔 [TRIGGER] 리뷰 댓글 알림 생성 완료: 사용자 {} -> 리뷰 작성자 {}", actorName, reviewUserId);
            } else {
                log.info("🔔 [TRIGGER] 자신의 리뷰에 댓글을 단 경우 알림 생성하지 않음: 사용자 {}", actorId);
            }

        } catch (Exception e) {
            log.error("🔔 [TRIGGER] 리뷰 댓글 알림 생성 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 에피소드 댓글 작성 시 알림 생성
     *
     * @param comment 새로 작성된 댓글
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void triggerEpisodeCommentNotification(EpisodeComment comment) {
        try {
            log.info("🔔 [TRIGGER] 에피소드 댓글 알림 트리거 시작 - Comment ID: {}", comment.getId());

            Long actorId = comment.getUser().getId();
            String actorName = comment.getUser().getName();
            Long animeId = comment.getEpisode().getAnime().getId();
            Long episodeId = comment.getEpisode().getId();

            log.info("🔔 [TRIGGER] 댓글 작성자: {}, 애니메이션: {}, 에피소드: {}", actorId, animeId, episodeId);

            // 1. 대댓글인 경우: 원댓글 작성자에게 알림
            if (comment.getParent() != null) {
                Long parentCommentUserId = comment.getParent().getUser().getId();

                log.info("🔔 [TRIGGER] 대댓글 감지 - 원댓글 작성자: {}, 현재 작성자: {}", parentCommentUserId, actorId);

                // 자신의 댓글에 대댓글을 단 경우 알림 생성하지 않음
                if (!parentCommentUserId.equals(actorId)) {
                    Long contentId = comment.getParent().getId();

                    notificationService.createCommentActivityNotification(
                            parentCommentUserId,
                            actorName,
                            "COMMENT_REPLY",
                            "EPISODE_COMMENT",
                            contentId,
                            animeId,
                            episodeId,
                            comment.getContent());

                    log.info("🔔 [TRIGGER] 에피소드 대댓글 알림 생성: 사용자 {} -> 댓글 작성자 {}", actorName, parentCommentUserId);
                }
            } else {
                // 2. 일반 댓글인 경우: 알림 대상이 없음
                // 에피소드 댓글은 특정한 알림 받을 대상이 명확하지 않음
                log.info("🔔 [TRIGGER] 일반 댓글 감지 - 알림 대상 없음 (에피소드 댓글은 특정 대상자 없음)");
            }

        } catch (Exception e) {
            log.error("🔔 [TRIGGER] 에피소드 댓글 알림 생성 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}
