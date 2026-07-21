package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.admin.AdminEpisodeDetailDto;
import com.ottproject.ottbackend.dto.admin.EpisodeCreateRequest;
import com.ottproject.ottbackend.dto.admin.EpisodeUpdateRequest;
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

import java.util.Comparator;
import java.util.List;

/**
 * AdminEpisodeService
 *
 * 큰 흐름
 * - 관리자가 작품에 새 화수를 등록하고, 저장 직후 찜한 사용자에게 업데이트 알림을 발송한다.
 *
 * 메서드 개요
 * - createEpisode: 에피소드 저장 + 관심작품 알림 트리거
 * - listEpisodes: 작품의 화수 목록 조회
 * - updateEpisode: 화수 메타데이터 부분 수정
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AdminEpisodeService {

    private final AnimeRepository animeRepository;
    private final EpisodeRepository episodeRepository;
    private final NotificationTriggerService notificationTriggerService;
    private final AnimeCacheService animeCacheService;

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

        // 상세 공용부에 에피소드 목록이 포함되므로 커밋 후 무효화한다.
        animeCacheService.evictDetail(animeId);

        return AdminEpisodeDetailDto.from(saved);
    }

    /**
     * 작품의 화수 목록 조회 (화수 오름차순).
     *
     * 락 없는 조회만 쓴다 — 이 트랜잭션은 readOnly 라 PostgreSQL 이 SELECT ... FOR UPDATE 를 거부한다.
     * (같은 함정으로 큐레이션 단건 조회가 500 을 냈다. AnimeRepository.findByIdWithoutLock 주석 참고)
     */
    @Transactional(readOnly = true)
    public List<AdminEpisodeDetailDto> listEpisodes(Long animeId) {
        requireAnimeExistsForRead(animeId);

        return episodeRepository.findByAnime_Id(animeId).stream()
                .sorted(Comparator.comparing(Episode::getEpisodeNumber))
                .map(AdminEpisodeDetailDto::from)
                .toList();
    }

    /**
     * 화수 메타데이터 부분 수정.
     *
     * 전달하지 않은(null) 필드는 그대로 둔다. save() 를 부르지 않는 것은 이 프로젝트 관례다 —
     * 영속 엔티티라 더티 체킹이 커밋 시점에 UPDATE 를 만든다(AnimeCurationService.update 참고).
     *
     * 알림은 보내지 않는다. 이건 새 화수가 아니라 기존 화수의 수정이라, 찜한 사용자에게 다시 알리면 스팸이 된다.
     */
    public AdminEpisodeDetailDto updateEpisode(Long animeId, Long episodeId, EpisodeUpdateRequest request) {
        Episode episode = episodeRepository.findById(episodeId) // 쓰기 트랜잭션이라 락을 잡아도 된다
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "에피소드가 존재하지 않습니다."));

        // 경로의 작품과 실제 소속이 다르면 남의 작품 화수를 고치는 셈이 된다.
        if (!episode.getAnime().getId().equals(animeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 작품의 에피소드가 아닙니다.");
        }

        if (request == null) {
            return AdminEpisodeDetailDto.from(episode);
        }

        applyIfPresent(request.getTitle(), "제목", episode::setTitle);
        applyIfPresent(request.getThumbnailUrl(), "썸네일 URL", episode::setThumbnailUrl);
        applyIfPresent(request.getVideoUrl(), "영상 URL", episode::setVideoUrl);

        if (request.getIsActive() != null) episode.setIsActive(request.getIsActive());
        if (request.getIsReleased() != null) episode.setIsReleased(request.getIsReleased());

        log.info("에피소드 수정 - animeId: {}, episodeId: {}", animeId, episodeId);

        // 상세 공용부에 에피소드 목록이 포함되므로 커밋 후 무효화한다.
        animeCacheService.evictDetail(animeId);

        return AdminEpisodeDetailDto.from(episode);
    }

    /**
     * 부분 수정 적용: null 이면 건드리지 않고, 빈 문자열은 400 으로 거절한다.
     * not-null 컬럼이라 빈 값이 들어가면 재생/노출이 조용히 깨진다.
     */
    private void applyIfPresent(String newValue, String fieldLabel, java.util.function.Consumer<String> setter) {
        if (newValue == null) {
            return;
        }
        if (newValue.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldLabel + "은(는) 빈 값일 수 없습니다.");
        }
        setter.accept(newValue.trim());
    }

    private void requireAnimeExistsForRead(Long animeId) {
        if (animeRepository.findByIdWithoutLock(animeId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "애니메이션이 존재하지 않습니다.");
        }
    }
}
