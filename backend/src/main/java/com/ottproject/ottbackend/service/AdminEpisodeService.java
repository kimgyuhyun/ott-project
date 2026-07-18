package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.admin.AdminEpisodeDetailDto;
import com.ottproject.ottbackend.dto.admin.EpisodeCreateRequest;
import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.entity.Episode;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.EpisodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * AdminEpisodeService
 *
 * 큰 흐름
 * - 관리자가 작품에 새 화수를 등록하고, 저장 직후 찜한 사용자에게 업데이트 알림을 발송한다.
 *
 * 메서드 개요
 * - createEpisode: 에피소드 저장 + 관심작품 알림 트리거
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AdminEpisodeService {

    private final AnimeRepository animeRepository;
    private final EpisodeRepository episodeRepository;
    private final NotificationTriggerService notificationTriggerService;

    /**
     * 에피소드 등록
     *
     * 알림은 저장이 끝난 뒤에 발송한다. 트리거는 REQUIRES_NEW 로 별도 트랜잭션에서 돌기 때문에
     * 아직 flush 되지 않은 에피소드는 알림 쪽에서 보이지 않는다.
     */
    public AdminEpisodeDetailDto createEpisode(Long animeId, EpisodeCreateRequest request) {
        Anime anime = animeRepository.findById(animeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "애니메이션이 존재하지 않습니다."));

        if (request == null || request.getEpisodeNumber() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "화수는 필수입니다.");
        }

        boolean duplicated = episodeRepository.findByAnime_Id(animeId).stream()
                .anyMatch(e -> request.getEpisodeNumber().equals(e.getEpisodeNumber()));
        if (duplicated) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "이미 등록된 화수입니다: " + request.getEpisodeNumber());
        }

        Episode episode;
        try {
            episode = Episode.createEpisode(
                    anime,
                    request.getEpisodeNumber(),
                    request.getTitle(),
                    request.getThumbnailUrl(),
                    request.getVideoUrl(),
                    request.getDuration()
            );
        } catch (IllegalArgumentException e) {
            // 팩토리의 필수값 검증은 도메인 규칙이다. 500 이 아니라 400 으로 돌려준다.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        Episode saved = episodeRepository.saveAndFlush(episode);
        log.info("에피소드 등록 - animeId: {}, episodeNumber: {}", animeId, saved.getEpisodeNumber());

        notificationTriggerService.triggerEpisodeUpdateNotification(saved);

        return AdminEpisodeDetailDto.from(saved);
    }
}
