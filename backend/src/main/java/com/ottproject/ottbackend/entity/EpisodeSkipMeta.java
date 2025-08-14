package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 에피소드 스킵 구간 메타 엔티티
 * - 인트로/엔딩 구간(초) 저장
 */
@Entity // 스킵 구간 메타
@Table(name = "episode_skip_meta", uniqueConstraints = @UniqueConstraint(columnNames = {"episode_id"})) // 1:1
@Getter
@Setter
@Builder @NoArgsConstructor
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
	@Column(nullable = false) @Builder.Default // 갱신 시각
	private LocalDateTime updatedAt = LocalDateTime.now(); // 갱신
}