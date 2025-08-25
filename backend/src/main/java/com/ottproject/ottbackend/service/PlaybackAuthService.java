package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.EpisodeRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import com.ottproject.ottbackend.util.HlsSignedUrlUtil;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * PlaybackAuthService
 *
 * 큰 흐름
 * - 재생 권한 검사와 Nginx secure_link 기반 서명 URL 생성을 제공한다.
 * - 1~3화 무료, 4화 이상 멤버십 필요 규칙을 적용한다.
 *
 * 메서드 개요
 * - canStream: 에피소드 재생 권한 여부 판단
 * - buildSignedStreamUrl: 품질 제한 적용 후 서명 URL 생성
 * - nextEpisodeId: 현재 화 기준 다음 화 ID 조회
 */
@Service
@Lazy
@RequiredArgsConstructor
public class PlaybackAuthService { // 재생 권한/URL 발급
	private final UserRepository userRepository; // 권한 확인(멤버십 여부 판단용)
	private final MembershipService membershipService; // 멤버십 추상화
	private final com.ottproject.ottbackend.repository.AnimeRepository animeListRepository; // 작품 소속 판단(에피소드 → ani)
	private final EpisodeRepository episodeRepository; // 에피소드 조회(존재/소속)

	/**
	 * 사용자의 특정 에피소드 재생 가능 여부 판단
	 */
	@Transactional(readOnly = true)
	public boolean canStream(Long userId, Long episodeId) { // 권한 검사
		if (userId == null) return false; // 미로그인 차단

		var episode = episodeRepository.findById(episodeId).orElse(null); // 에피소드 조회
		if (episode == null || Boolean.FALSE.equals(episode.getIsActive()) || Boolean.FALSE.equals(episode.getIsReleased())) {
			return false; // 비활성/미공개 차단
		}

		Integer epNo = episode.getEpisodeNumber(); // 화수
		if (epNo != null && epNo <= 3) return true; // 1~3화 무료

		return membershipService.isMember(userId); // 4화 이상은 멤버십 필요
	}

	/**
	 * Nginx secure_link 기반 m3u8 서명 URL 생성
	 */
	@Transactional(readOnly = true)
	public String buildSignedStreamUrl(Long userId, Long episodeId) { // 서명URL 생성
		var episode = episodeRepository.findById(episodeId).orElseThrow(); // 에피소드 존재 보장
		boolean isMember = membershipService.isMember(userId); // 멤버십 여부

		String absolute = episode.getVideoUrl(); // 원본 절대 URL
		String filtered = isMember ? absolute : absolute.replace("master.m3u8", "master_720p.m3u8"); // 품질 제한 분기

		String uriPath = filtered.replaceFirst("https?://[^/]+", ""); // 도메인 제거해 path 추출

		long expires = HlsSignedUrlUtil.defaultExpiryFromNowSeconds(600); // TTL 10분
		String secret = System.getProperty("secure.link.secret", System.getenv().getOrDefault("SECURE_LINK_SECRET", "change_me")); // 시크릿 로드
		String st = HlsSignedUrlUtil.generateSignature(uriPath, expires, secret); // 서명 생성

		String join = filtered.contains("?") ? "&" : "?"; // 쿼리 구분자
		return filtered + join + "e=" + expires + "&st=" + st; // 최종 URL 반환
	}

	/**
	 * 현재 화 기준 다음 화 ID 조회(공개된 것만)
	 */
	@Transactional(readOnly = true)
	public Long nextEpisodeId(Long currentEpisodeId) { // 다음 화 조회
		var current = episodeRepository.findById(currentEpisodeId).orElse(null); // 현재 화
		if (current == null || current.getAnime() == null) {
			return null; // 없으면 null
		}
		var next = episodeRepository
				.findFirstByAnimeIdAndEpisodeNumberGreaterThanAndIsReleasedTrueOrderByEpisodeNumberAsc(
						current.getAnime().getId(), current.getEpisodeNumber()
				); // NEW 다음 화 탐색
		return (next != null) ? next.getId() : null; // 다음 화 ID 또는 null
	}
}