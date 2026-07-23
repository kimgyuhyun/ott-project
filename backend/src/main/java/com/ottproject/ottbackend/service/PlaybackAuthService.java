package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.EpisodeRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import com.ottproject.ottbackend.mybatis.EpisodeMapper;
import com.ottproject.ottbackend.util.HlsSignedUrlUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * PlaybackAuthService
 *
 * 큰 흐름
 * - 재생 권한 검사와 secure_link 서명 URL 생성을 제공한다.
 * - 1~3화 무료, 4화 이상 멤버십 필요 규칙을 적용한다.
 *
 * 메서드 개요
 * - canStream: 에피소드 재생 권한 여부 판단
 * - buildSignedStreamUrl: master.m3u8 에 서명(e/st) 부착 후 URL 반환
 *
 * 배포 메모
 * - 영상은 R2 의 실제 다화질 HLS 이고, Cloudflare Worker 엣지가 이 서명을
 *   실제로 검증한다. 여기서는 master.m3u8 하나만 서명(TTL 6h)하고, 하위
 *   재생목록·세그먼트는 Worker 가 응답을 되쓰며 캐스케이드로 서명한다.
 * - 접근 제어는 canStream(발급 게이트) + Worker(엣지 서명 검증) 2단.
 *   R2 공개 접근은 꺼서 Worker 가 유일한 경로다.
 * - 서명은 URL-safe base64(무패딩), 시크릿은 SECURE_LINK_SECRET(양측 공유).
 */
@Slf4j
@Service
@Lazy
@RequiredArgsConstructor
public class PlaybackAuthService { // 재생 권한/URL 발급
	private final UserRepository userRepository; // 권한 확인(멤버십 여부 판단용)
	private final MembershipService membershipService; // 멤버십 추상화
	private final com.ottproject.ottbackend.repository.AnimeRepository animeListRepository; // 작품 소속 판단(에피소드 → ani)
	private final EpisodeMapper episodeMapper; // 에피소드 조회용 MyBatis 매퍼

	/**
	 * 사용자의 특정 에피소드 재생 가능 여부 판단
	 * - 멤버십 상태를 실시간으로 확인하여 정확한 권한 판단
	 */
	@Transactional(readOnly = true)
	public boolean canStream(Long userId, Long episodeId) { // 권한 검사
		if (userId == null) return false; // 미로그인 차단

		var episode = episodeMapper.findEpisodeById(episodeId); // MyBatis로 에피소드 조회
		if (episode == null || Boolean.FALSE.equals(episode.getIsActive()) || Boolean.FALSE.equals(episode.getIsReleased())) {
			log.warn("에피소드 접근 차단 - episodeId: {}, isActive: {}, isReleased: {}", 
				episodeId, episode != null ? episode.getIsActive() : null, episode != null ? episode.getIsReleased() : null);
			return false; // 비활성/미공개 차단
		}

		Integer epNo = episode.getEpisodeNumber(); // 화수
		if (epNo != null && epNo <= 3) {
			log.debug("무료 에피소드 접근 허용 - episodeId: {}, episodeNumber: {}", episodeId, epNo);
			return true; // 1~3화 무료
		}

		// 4화 이상은 멤버십 필요 - 실시간 상태 확인
		boolean isMember = membershipService.isMember(userId);
		log.debug("멤버십 에피소드 접근 확인 - episodeId: {}, episodeNumber: {}, isMember: {}", 
			episodeId, epNo, isMember);
		
		return isMember; // 4화 이상은 멤버십 필요
	}

	/**
	 * Nginx secure_link 기반 m3u8 서명 URL 생성
	 */
	@Transactional(readOnly = true)
	public String buildSignedStreamUrl(Long userId, Long episodeId) { // 서명URL 생성
		var episode = episodeMapper.findEpisodeById(episodeId); // MyBatis로 에피소드 조회
		if (episode == null) {
			throw new RuntimeException("Episode not found: " + episodeId);
		}

		String absolute = episode.getVideoUrl(); // 원본 절대 URL
		if (absolute == null || absolute.isBlank()) {
			throw new IllegalStateException("Episode video URL is not set: " + episodeId); // 원본 URL 누락 방어(NPE 방지)
		}

		// 화질 티어링 없음: 접근은 canStream 게이트로 허용/차단만 하고,
		// 허용된 사용자는 동일한 다화질 HLS 사다리를 그대로 받는다.
		String uriPath = absolute.replaceFirst("https?://[^/]+", ""); // 도메인 제거해 path 추출

		// TTL 6시간: 엣지 캐스케이드가 master 만료값을 하위 세그먼트까지 공유하므로,
		// 최장 콘텐츠 재생 + 일시정지/탐색 여유를 덮어야 세션 중간에 403 이 안 난다.
		long expires = HlsSignedUrlUtil.defaultExpiryFromNowSeconds(6 * 3600);
		String secret = System.getProperty("secure.link.secret", System.getenv().get("SECURE_LINK_SECRET")); // 시크릿 로드
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("SECURE_LINK_SECRET is not configured"); // 시크릿 미설정 시 서명 위조 방지를 위해 즉시 실패
		}
		String st = HlsSignedUrlUtil.generateSignature(uriPath, expires, secret); // 마스터 진입 서명(하위 세그먼트는 엣지가 캐스케이드 서명)

		String join = absolute.contains("?") ? "&" : "?"; // 쿼리 구분자
		return absolute + join + "e=" + expires + "&st=" + st; // 최종 URL 반환
	}
}