package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.AnimeFavorite;
import com.ottproject.ottbackend.entity.Episode;
import com.ottproject.ottbackend.entity.CommentLike;
import com.ottproject.ottbackend.entity.EpisodeCommentLike;
import com.ottproject.ottbackend.entity.EpisodeComment;
import com.ottproject.ottbackend.repository.AnimeFavoriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    @Transactional
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
    @Transactional
    public void triggerCommentLikeNotification(CommentLike like) {
        try {
            // 리뷰 댓글인지 에피소드 댓글인지 확인
            if (like.getComment() != null) {
                Long userId = like.getComment().getUser().getId();
                String actorName = like.getUser().getName();
                Long contentId = like.getComment().getId();
                Long animeId = like.getComment().getReview().getAnime().getId();

                notificationService.createCommentActivityNotification(
                        userId, actorName, "COMMENT_LIKE", "REVIEW_COMMENT", 
                        contentId, animeId, null, null);

                log.info("리뷰 댓글 좋아요 알림 생성: 사용자 {} -> 댓글 작성자 {}", actorName, userId);
            }
            
        } catch (Exception e) {
            log.error("댓글 좋아요 알림 생성 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 에피소드 댓글 좋아요 시 알림 생성
     * 
     * @param like 새로 생성된 좋아요
     */
    @Transactional
    public void triggerEpisodeCommentLikeNotification(EpisodeCommentLike like) {
        try {
            EpisodeComment comment = like.getEpisodeComment();
            if (comment == null) return;

            Long userId = comment.getUser().getId();
            String actorName = like.getUser().getName();
            Long contentId = comment.getId();
            Long animeId = comment.getEpisode().getAnime().getId();
            Long episodeId = comment.getEpisode().getId();

            notificationService.createCommentActivityNotification(
                    userId, actorName, "COMMENT_LIKE", "EPISODE_COMMENT", 
                    contentId, animeId, episodeId, null);

            log.info("에피소드 댓글 좋아요 알림 생성: 사용자 {} -> 댓글 작성자 {}", actorName, userId);
            
        } catch (Exception e) {
            log.error("에피소드 댓글 좋아요 알림 생성 중 오류 발생: {}", e.getMessage(), e);
        }
    }


    /**
     * 에피소드 댓글 작성 시 알림 생성
     * 
     * @param comment 새로 작성된 댓글
     */
    @Transactional
    public void triggerEpisodeCommentNotification(EpisodeComment comment) {
        try {
            // 대댓글인 경우에만 알림 생성 (원댓글 작성자에게)
            if (comment.getParent() != null) {
                Long userId = comment.getParent().getUser().getId();
                String actorName = comment.getUser().getName();
                Long contentId = comment.getParent().getId();
                Long animeId = comment.getEpisode().getAnime().getId();
                Long episodeId = comment.getEpisode().getId();

                notificationService.createCommentActivityNotification(
                        userId, actorName, "COMMENT_REPLY", "EPISODE_COMMENT", 
                        contentId, animeId, episodeId, comment.getContent());

                log.info("에피소드 대댓글 알림 생성: 사용자 {} -> 댓글 작성자 {}", actorName, userId);
            }
            
        } catch (Exception e) {
            log.error("에피소드 댓글 알림 생성 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}
