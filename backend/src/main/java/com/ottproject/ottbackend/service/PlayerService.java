package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.SubtitleDto;
import com.ottproject.ottbackend.dto.SkipMetaResponseDto;
import com.ottproject.ottbackend.dto.SkipUsageRequestDto;
import com.ottproject.ottbackend.dto.EpisodeProgressResponseDto;
import com.ottproject.ottbackend.entity.Subtitle;
import com.ottproject.ottbackend.entity.EpisodeSkipMeta;
import com.ottproject.ottbackend.entity.SkipUsage;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.entity.EpisodeProgress;
import com.ottproject.ottbackend.enums.SkipType;
import com.ottproject.ottbackend.repository.SubtitleRepository;
import com.ottproject.ottbackend.repository.EpisodeSkipMetaRepository;
import com.ottproject.ottbackend.repository.SkipUsageRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import com.ottproject.ottbackend.repository.EpisodeRepository;
import com.ottproject.ottbackend.repository.EpisodeProgressRepository;
import com.ottproject.ottbackend.mybatis.EpisodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * PlayerService
 *
 * 큰 흐름
 * - 플레이어 도메인의 모든 비즈니스 로직을 담당하는 서비스
 * - 자막, 스킵, 다음 에피소드, 스트림 URL, 진행률 등 플레이어 사용과 관련된 모든 기능 제공
 *
 * 메서드 개요
 * - 자막 관련: getSubtitlesByEpisode, getDefaultSubtitle, getSubtitleByLanguage
 * - 스킵 관련: getSkipMetaByEpisode, trackUsage
 * - 에피소드 관련: getNextEpisode, getStreamUrl, canStream
 * - 진행률 관련: saveProgress, getProgress, getBulkProgress
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
    private final EpisodeProgressRepository progressRepository;
    private final EpisodeMapper episodeMapper;
    private final PlaybackAuthService playbackAuthService;

    // === 자막 관련 기능 ===
    
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

    // === 스킵 관련 기능 ===
    
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

    // === 에피소드 관련 기능 ===
    
    /**
     * 다음 에피소드 정보 조회
     * @param currentEpisodeId 현재 에피소드 ID
     * @return 다음 에피소드 정보 (없으면 null)
     */
    public com.ottproject.ottbackend.dto.EpisodeDto getNextEpisode(Long currentEpisodeId) {
        var current = episodeMapper.findEpisodeById(currentEpisodeId);
        if (current == null || current.getAnimeId() == null) {
            return null;
        }
        return episodeMapper.findNextEpisode(current.getAnimeId(), current.getEpisodeNumber());
    }
    
    /**
     * 사용자별 스트림 URL 생성
     * @param userId 사용자 ID
     * @param episodeId 에피소드 ID
     * @return 서명된 스트림 URL
     */
    public String getStreamUrl(Long userId, Long episodeId) {
        return playbackAuthService.buildSignedStreamUrl(userId, episodeId);
    }
    
    /**
     * 에피소드 재생 권한 검사
     * @param userId 사용자 ID
     * @param episodeId 에피소드 ID
     * @return 재생 가능 여부
     */
    public boolean canStream(Long userId, Long episodeId) {
        return playbackAuthService.canStream(userId, episodeId);
    }

    // === 진행률 관련 기능 ===
    
    /**
     * 진행률 멱등 저장(있으면 갱신, 없으면 생성) - 동시성 안전
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void saveProgress(Long userId, Long episodeId, Integer positionSec, Integer durationSec) {
        System.out.println("🔍 saveProgress 호출:");
        System.out.println("  - userId: " + userId);
        System.out.println("  - episodeId: " + episodeId);
        System.out.println("  - positionSec: " + positionSec);
        System.out.println("  - durationSec: " + durationSec);
        
        try {
            // 동시성 안전을 위해 먼저 조회
            EpisodeProgress entity = progressRepository.findByUser_IdAndEpisode_Id(userId, episodeId)
                    .orElseGet(() -> {
                        System.out.println("  - 새 레코드 생성");
                        // 사용자와 에피소드 존재 여부 확인
                        var user = userRepository.findById(userId);
                        var episode = episodeRepository.findById(episodeId);
                        
                        if (user.isEmpty()) {
                            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId);
                        }
                        if (episode.isEmpty()) {
                            throw new IllegalArgumentException("에피소드를 찾을 수 없습니다: " + episodeId);
                        }
                        
                        return EpisodeProgress.builder()
                                .user(user.get())
                                .episode(episode.get())
                                .positionSec(0).durationSec(0).build();
                    });
            
            if (entity.getId() != null) {
                System.out.println("  - 기존 레코드 업데이트 (ID: " + entity.getId() + ")");
                System.out.println("  - 기존 positionSec: " + entity.getPositionSec());
                System.out.println("  - 기존 durationSec: " + entity.getDurationSec());
            }
            
            // 값 검증
            if (positionSec != null && positionSec >= 0) {
                entity.setPositionSec(positionSec);
            }
            if (durationSec != null && durationSec > 0) {
                entity.setDurationSec(durationSec);
            }
            
            // 진행률이 총 길이를 초과하지 않도록 검증
            if (entity.getPositionSec() > entity.getDurationSec()) {
                System.out.println("  ⚠️ 진행률이 총 길이를 초과, 조정: " + entity.getPositionSec() + " -> " + entity.getDurationSec());
                entity.setPositionSec(entity.getDurationSec());
            }
            
            System.out.println("  - 업데이트 후 positionSec: " + entity.getPositionSec());
            System.out.println("  - 업데이트 후 durationSec: " + entity.getDurationSec());
            
            EpisodeProgress saved = progressRepository.save(entity);
            System.out.println("  - 저장 완료 (ID: " + saved.getId() + ")");
            
        } catch (Exception e) {
            System.err.println("  ❌ 진행률 저장 실패: " + e.getMessage());
            throw new RuntimeException("진행률 저장 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 진행률 단건 조회
     */
    public java.util.Optional<EpisodeProgressResponseDto> getProgress(Long userId, Long episodeId) {
        return progressRepository.findByUser_IdAndEpisode_Id(userId, episodeId)
                .map(p -> EpisodeProgressResponseDto.builder()
                        .positionSec(p.getPositionSec())
                        .durationSec(p.getDurationSec())
                        .updatedAt(p.getUpdatedAt())
                        .build());
    }
    
    /**
     * 진행률 벌크 조회(에피소드 ID 집합)
     */
    public Map<Long, EpisodeProgressResponseDto> getBulkProgress(Long userId, java.util.Collection<Long> episodeIds) {
        List<EpisodeProgress> list = progressRepository.findByUser_IdAndEpisode_IdIn(userId, episodeIds);
        Map<Long, EpisodeProgressResponseDto> map = new HashMap<>();
        
        for (EpisodeProgress p : list) {
            map.put(
                p.getEpisode().getId(),
                EpisodeProgressResponseDto.builder()
                    .positionSec(p.getPositionSec())
                    .durationSec(p.getDurationSec())
                    .updatedAt(p.getUpdatedAt())
                    .build()
            );
        }
        return map;
    }
    
    /**
     * 사용자의 시청 기록 조회 (페이지네이션)
     */
    public Map<String, Object> getWatchHistory(Long userId, int page, int size) {
        // 페이지네이션 계산
        int offset = page * size;
        
        // 사용자의 진행률이 있는 에피소드들을 조회
        var progressList = progressRepository.findByUser_IdOrderByUpdatedAtDesc(userId, 
            org.springframework.data.domain.PageRequest.of(page, size));
        
        // 결과 구성
        Map<String, Object> result = new HashMap<>();
        result.put("content", progressList.getContent().stream()
            .map(p -> Map.of(
                "episodeId", p.getEpisode().getId(),
                "animeId", p.getEpisode().getAnime().getId(),
                "episodeNumber", p.getEpisode().getEpisodeNumber(),
                "positionSec", p.getPositionSec(),
                "durationSec", p.getDurationSec(),
                "updatedAt", p.getUpdatedAt()
            ))
            .collect(Collectors.toList()));
        result.put("totalElements", progressList.getTotalElements());
        result.put("totalPages", progressList.getTotalPages());
        result.put("currentPage", page);
        result.put("size", size);
        
        return result;
    }

    // === Private 변환 메서드들 ===

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
