package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.BingeWatchDto;
import com.ottproject.ottbackend.mybatis.BingeWatchMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 정주행 서비스
 *
 * 큰 흐름
 * - 사용자의 정주행 완료 작품을 조회한다.
 * - 완결 작품 중 모든 에피소드를 90% 이상 시청한 작품을 정주행으로 간주한다.
 *
 * 메서드 개요
 * - getBingeWatchedAnimes: 사용자별 정주행 완료 작품 목록 조회
 */
@Service
@RequiredArgsConstructor
public class BingeWatchService {

    private final BingeWatchMapper bingeWatchMapper;

    /**
     * 사용자별 정주행 완료 작품 목록 조회
     *
     * @param userId 사용자 ID
     * @return 정주행 완료 작품 목록
     */
    public List<BingeWatchDto> getBingeWatchedAnimes(Long userId) {
        System.out.println("🔧 [SERVICE] BingeWatchService.getBingeWatchedAnimes 시작");
        System.out.println("🔧 [SERVICE] 파라미터 - userId: " + userId);

        List<BingeWatchDto> result = bingeWatchMapper.findBingeWatchedAnimes(userId);

        System.out.println("🔧 [SERVICE] 조회 결과 - 정주행 완료 작품 수: " + result.size());
        if (!result.isEmpty()) {
            System.out.println("🔧 [SERVICE] 첫 번째 정주행 작품:");
            BingeWatchDto first = result.get(0);
            System.out.println("  - aniId: " + first.getAniId());
            System.out.println("  - title: " + first.getTitle());
            System.out.println("  - totalEpisodes: " + first.getTotalEpisodes());
            System.out.println("  - watchedEpisodes: " + first.getWatchedEpisodes());
            System.out.println("  - completedAt: " + first.getCompletedAt());
        }

        return result;
    }
}
