package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.repository.EpisodeProgressRepository;
import com.ottproject.ottbackend.mybatis.EpisodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 최근본 애니메이션 서비스
 *
 * 큰 흐름
 * - 사용자의 최근본 목록을 관리한다.
 * - 삭제 시 시청 기록은 유지하고 최근본 목록에서만 숨김 처리한다.
 *
 * 메서드 개요
 * - hideFromRecent: 최근본 목록에서 숨김 처리
 */
@Service
@RequiredArgsConstructor
public class RecentAnimeService {
    
    private final EpisodeProgressRepository progressRepository;
    private final EpisodeMapper episodeMapper;
    
    /**
     * 최근본 목록에서 숨김 처리
     * 해당 애니메이션의 모든 에피소드 진행률을 hidden_in_recent = true로 설정
     * 
     * @param userId 사용자 ID
     * @param aniId 애니메이션 ID
     */
    @Transactional
    public void hideFromRecent(Long userId, Long aniId) {
        System.out.println("🔧 [SERVICE] RecentAnimeService.hideFromRecent 시작");
        System.out.println("🔧 [SERVICE] 파라미터 - userId: " + userId + ", aniId: " + aniId);
        
        // 해당 애니메이션의 모든 에피소드 ID 조회
        List<Long> episodeIds = episodeMapper.findEpisodesByAnimeId(aniId)
            .stream()
            .map(episode -> episode.getId())
            .toList();
        
        System.out.println("🔧 [SERVICE] 숨김 처리할 에피소드 수: " + episodeIds.size());
        
        if (!episodeIds.isEmpty()) {
            // 해당 에피소드들의 진행률을 hidden_in_recent = true로 업데이트
            progressRepository.updateHiddenInRecentByUserAndEpisodes(userId, episodeIds, true);
            System.out.println("🔧 [SERVICE] 최근본 목록에서 숨김 처리 완료");
        }
    }
    
    /**
     * 정주행 목록에서 완전 삭제 (시청 기록 완전 삭제)
     * 해당 애니메이션의 모든 에피소드 진행률을 완전히 삭제
     * 
     * @param userId 사용자 ID
     * @param aniId 애니메이션 ID
     */
    @Transactional
    public void deleteFromBinge(Long userId, Long aniId) {
        System.out.println("🔧 [SERVICE] RecentAnimeService.deleteFromBinge 시작");
        System.out.println("🔧 [SERVICE] 파라미터 - userId: " + userId + ", aniId: " + aniId);
        
        // 해당 애니메이션의 모든 에피소드 ID 조회
        List<Long> episodeIds = episodeMapper.findEpisodesByAnimeId(aniId)
            .stream()
            .map(episode -> episode.getId())
            .toList();
        
        System.out.println("🔧 [SERVICE] 삭제할 에피소드 수: " + episodeIds.size());
        
        if (!episodeIds.isEmpty()) {
            // 해당 에피소드들의 진행률을 완전히 삭제
            progressRepository.deleteByUser_IdAndEpisode_IdIn(userId, episodeIds);
            System.out.println("🔧 [SERVICE] 정주행 목록에서 완전 삭제 완료");
        }
    }
}
