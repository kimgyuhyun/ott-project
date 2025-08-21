package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.mybatis.PlayerProgressQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * PlayerProgressReadService
 *
 * 큰 흐름
 * - 진행률 관련 읽기 전용 집계를 MyBatis 매퍼로 수행한다.
 *
 * 메서드 개요
 * - sumWatchedSecondsSincePaidEpisodes: 결제시각 이후 4화 이상 누적 시청 초 합
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


