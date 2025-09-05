package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 시청 진행률 엔티티
 *
 * 큰 흐름
 * - 사용자×에피소드의 현재 시청 위치/길이를 저장한다.
 * - 유니크 제약으로 1 사용자당 1 에피소드 1 레코드만 보장한다.
 * - 마지막 수정 시각으로 최신 시청 시점을 노출한다.
 *
 * 필드 개요
 * - id/user/episode: 식별/소유자/대상 회차
 * - positionSec/durationSec: 현재 위치/총 길이(초)
 * - updatedAt: 최근 갱신 시각
 */
@Entity // 진행률 저장 엔티티
@Table(name = "episode_progress", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","episode_id"})) // 유니크
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EpisodeProgress { // 시청 진행률

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) // PK
	private Long id; // PK

	// 진행률 소유 사용자
	@ManyToOne(fetch = FetchType.LAZY, optional = false) // 사용자
	@JoinColumn(name = "user_id", nullable = false) // FK
	private User user; // 사용자

	// 진행률 대상 에피소드
	@ManyToOne(fetch = FetchType.LAZY, optional = false) // 에피소드
	@JoinColumn(name = "episode_id", nullable = false) // FK
	private Episode episode; // 에피소드

	// 현재 위치(초)
	@Column(nullable = false) // 진행 위치(초)
	private Integer positionSec; // 현재 위치

	// 총 길이(초)
	@Column(nullable = false) // 총 길이(초)
	private Integer durationSec; // 총 길이

	// 최신 갱신 시각
	@LastModifiedDate
	@Column(nullable = false) // 수정 시각
	private LocalDateTime updatedAt; // 갱신 시각

	// ===== 정적 팩토리 메서드 =====

	/**
	 * 에피소드 진행도 생성 (비즈니스 로직 캡슐화)
	 * 
	 * @param user 사용자
	 * @param episode 에피소드
	 * @param currentTime 현재 시청 시간 (초)
	 * @return 생성된 EpisodeProgress 엔티티
	 * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
	 */
	public static EpisodeProgress createProgress(User user, Episode episode, Integer currentTime) {
		// 필수 필드 검증
		if (user == null) {
			throw new IllegalArgumentException("사용자는 필수입니다.");
		}
		if (episode == null) {
			throw new IllegalArgumentException("에피소드는 필수입니다.");
		}
		if (currentTime == null || currentTime < 0) {
			throw new IllegalArgumentException("현재 시청 시간은 0 이상이어야 합니다.");
		}

		// EpisodeProgress 엔티티 생성
		EpisodeProgress progress = new EpisodeProgress();
		progress.user = user;
		progress.episode = episode;
		progress.positionSec = currentTime;
		progress.durationSec = 0; // 기본값, 나중에 업데이트

		return progress;
	}

	// ===== 비즈니스 메서드 =====

	/**
	 * 진행도 업데이트 (비즈니스 로직 캡슐화)
	 * 
	 * @param user 사용자
	 * @param episode 에피소드
	 * @param currentTime 현재 시청 시간 (초)
	 * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
	 */
	public void updateProgress(User user, Episode episode, Integer currentTime) {
		if (user == null) {
			throw new IllegalArgumentException("사용자는 필수입니다.");
		}
		if (episode == null) {
			throw new IllegalArgumentException("에피소드는 필수입니다.");
		}
		if (currentTime == null || currentTime < 0) {
			throw new IllegalArgumentException("현재 시청 시간은 0 이상이어야 합니다.");
		}
		if (this.durationSec != null && currentTime > this.durationSec) {
			throw new IllegalArgumentException("현재 시청 시간은 에피소드 길이를 초과할 수 없습니다.");
		}

		this.positionSec = currentTime;
	}
}