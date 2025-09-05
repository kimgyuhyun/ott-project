package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 스킵 구간 메타 엔티티
 *
 * 큰 흐름
 * - 인트로/엔딩 구간을 초 단위로 저장한다(회차별 1:1).
 * - 플레이어에서 자동/수동 스킵 버튼 렌더링에 사용한다.
 *
 * 필드 개요
 * - id/episode: 식별/대상 회차(1:1)
 * - introStart/introEnd/outroStart/outroEnd: 스킵 경계(초)
 * - updatedAt: 최근 갱신 시각
 */
@Entity // 스킵 구간 메타
@Table(name = "episode_skip_meta", uniqueConstraints = @UniqueConstraint(columnNames = {"episode_id"})) // 1:1
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EpisodeSkipMeta { // 오프닝/엔딩 구간

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) // PK
	private Long id; // PK

	// 대상 에피소드(1:1)
	@OneToOne(fetch = FetchType.LAZY, optional = false) // 에피소드 1:1
	@JoinColumn(name = "episode_id", nullable = false) // FK
	private Episode episode; // 에피소드

	@Column(nullable = true) private Integer introStart; // 인트로 시작(초)
	@Column(nullable = true) private Integer introEnd;   // 인트로 종료(초)
	@Column(nullable = true) private Integer outroStart; // 엔딩 시작(초)
	@Column(nullable = true) private Integer outroEnd;   // 엔딩 종료(초)

	@LastModifiedDate
	@Column(nullable = false) // 갱신 시각
	private LocalDateTime updatedAt; // 갱신

	// ===== 정적 팩토리 메서드 =====

	/**
	 * 스킵 메타 생성 (비즈니스 로직 캡슐화)
	 * 
	 * @param episode 에피소드
	 * @param startTime 스킵 시작 시간 (초)
	 * @param endTime 스킵 종료 시간 (초)
	 * @param skipType 스킵 유형
	 * @return 생성된 EpisodeSkipMeta 엔티티
	 * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
	 */
	public static EpisodeSkipMeta createSkipMeta(Episode episode, Integer startTime, Integer endTime, String skipType) {
		// 필수 필드 검증
		if (episode == null) {
			throw new IllegalArgumentException("에피소드는 필수입니다.");
		}
		if (startTime == null || startTime < 0) {
			throw new IllegalArgumentException("시작 시간은 0 이상이어야 합니다.");
		}
		if (endTime == null || endTime <= startTime) {
			throw new IllegalArgumentException("종료 시간은 시작 시간보다 커야 합니다.");
		}
		if (skipType == null || skipType.trim().isEmpty()) {
			throw new IllegalArgumentException("스킵 유형은 필수입니다.");
		}

		// EpisodeSkipMeta 엔티티 생성
		EpisodeSkipMeta meta = new EpisodeSkipMeta();
		meta.episode = episode;

		// 스킵 유형에 따라 설정
		if ("INTRO".equals(skipType.toUpperCase())) {
			meta.introStart = startTime;
			meta.introEnd = endTime;
		} else if ("OUTRO".equals(skipType.toUpperCase())) {
			meta.outroStart = startTime;
			meta.outroEnd = endTime;
		} else {
			throw new IllegalArgumentException("지원되지 않는 스킵 유형입니다. (INTRO|OUTRO)");
		}

		return meta;
	}

	// ===== 비즈니스 메서드 =====

	/**
	 * 인트로 구간 설정
	 * @param startTime 시작 시간 (초)
	 * @param endTime 종료 시간 (초)
	 * @throws IllegalArgumentException 시간이 유효하지 않은 경우
	 */
	public void setIntroSection(Integer startTime, Integer endTime) {
		if (startTime == null || startTime < 0) {
			throw new IllegalArgumentException("시작 시간은 0 이상이어야 합니다.");
		}
		if (endTime == null || endTime <= startTime) {
			throw new IllegalArgumentException("종료 시간은 시작 시간보다 커야 합니다.");
		}

		this.introStart = startTime;
		this.introEnd = endTime;
	}

	/**
	 * 엔딩 구간 설정
	 * @param startTime 시작 시간 (초)
	 * @param endTime 종료 시간 (초)
	 * @throws IllegalArgumentException 시간이 유효하지 않은 경우
	 */
	public void setOutroSection(Integer startTime, Integer endTime) {
		if (startTime == null || startTime < 0) {
			throw new IllegalArgumentException("시작 시간은 0 이상이어야 합니다.");
		}
		if (endTime == null || endTime <= startTime) {
			throw new IllegalArgumentException("종료 시간은 시작 시간보다 커야 합니다.");
		}

		this.outroStart = startTime;
		this.outroEnd = endTime;
	}
}