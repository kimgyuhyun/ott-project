package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.mybatis.PlayerProgressQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 플레이어 진행률 읽기 서비스
 * - MyBatis 매퍼를 통해 복잡 조회 수행
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerProgressReadService {
	private final PlayerProgressQueryMapper playerProgressQueryMapper;

	public int sumWatchedSecondsSincePaidEpisodes(Long userId, LocalDateTime since) {
		Integer v = playerProgressQueryMapper.sumWatchedSecondsSincePaidEpisodes(userId, since);
		return v == null ? 0 : v;
	}
}


