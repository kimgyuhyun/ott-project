package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.SubtitleDto;
import com.ottproject.ottbackend.dto.SkipMetaResponseDto;
import com.ottproject.ottbackend.dto.SkipUsageRequestDto;
import com.ottproject.ottbackend.entity.Subtitle;
import com.ottproject.ottbackend.entity.EpisodeSkipMeta;
import com.ottproject.ottbackend.entity.SkipUsage;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.SkipType;
import com.ottproject.ottbackend.repository.SubtitleRepository;
import com.ottproject.ottbackend.repository.EpisodeSkipMetaRepository;
import com.ottproject.ottbackend.repository.SkipUsageRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import com.ottproject.ottbackend.repository.EpisodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PlayerService
 *
 * 큰 흐름
 * - 플레이어에 필요한 자막, 스킵 정보를 조회하고 스킵 사용을 로깅한다.
 *
 * 메서드 개요
 * - getSubtitlesByEpisode: 에피소드별 자막 목록 조회
 * - getSkipMetaByEpisode: 에피소드별 스킵 정보 조회
 * - trackUsage: 스킵 사용 로깅
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerService {
    private final SubtitleRepository subtitleRepository;
    private final EpisodeSkipMetaRepository skipMetaRepository;
    private final SkipUsageRepository skipUsageRepository;
    private final UserRepository userRepository;
    private final EpisodeRepository episodeRepository;

    public List<SubtitleDto> getSubtitlesByEpisode(Long episodeId) {
        List<Subtitle> subtitles = subtitleRepository.findByEpisodeId(episodeId);
        return subtitles.stream()
                .map(this::convertToSubtitleDto)
                .collect(Collectors.toList());
    }

    public SubtitleDto getDefaultSubtitle(Long episodeId) {
        return subtitleRepository.findByEpisodeIdAndIsDefaultTrue(episodeId)
                .map(this::convertToSubtitleDto)
                .orElse(null);
    }

    public SubtitleDto getSubtitleByLanguage(Long episodeId, String language) {
        return subtitleRepository.findByEpisodeIdAndLanguage(episodeId, language)
                .map(this::convertToSubtitleDto)
                .orElse(null);
    }

    public SkipMetaResponseDto getSkipMetaByEpisode(Long episodeId) {
        return skipMetaRepository.findByEpisodeId(episodeId)
                .map(this::convertToSkipMetaDto)
                .orElse(null);
    }

    /**
     * 스킵 사용 로깅
     */
    @Transactional
    public void trackUsage(Long userId, Long episodeId, SkipType type, Integer atSec) {
        var usage = SkipUsage.builder()
                .user(userId != null ? userRepository.findById(userId).orElse(null) : null)
                .episode(episodeRepository.findById(episodeId).orElseThrow())
                .type(type)
                .atSec(atSec)
                .build();
        skipUsageRepository.save(usage);
    }

    /**
     * 스킵 사용 로깅 (문자열 타입)
     */
    @Transactional
    public void trackUsage(Long userId, Long episodeId, String type, Integer atSec) {
        SkipType t = null;
        if (type != null) {
            try {
                t = SkipType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        if (t == null) return;
        trackUsage(userId, episodeId, t, atSec);
    }



    private SubtitleDto convertToSubtitleDto(Subtitle subtitle) {
        return SubtitleDto.builder()
                .id(subtitle.getId())
                .episodeId(subtitle.getEpisode().getId())
                .language(subtitle.getLanguage())
                .url(subtitle.getUrl())
                .isDefault(subtitle.isDefault())
                .build();
    }

    private SkipMetaResponseDto convertToSkipMetaDto(EpisodeSkipMeta skipMeta) {
        return SkipMetaResponseDto.builder()
                .introStart(skipMeta.getIntroStart())
                .introEnd(skipMeta.getIntroEnd())
                .outroStart(skipMeta.getOutroStart())
                .outroEnd(skipMeta.getOutroEnd())
                .build();
    }


}
