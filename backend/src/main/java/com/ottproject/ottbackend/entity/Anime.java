package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 애니메이션 기본 + 상세 정보를 통합 저장하는 엔티티
 * - 기존 AnimeList(목록) + AnimeDetail(상세) 필드를 한 곳에서 관리
 * - 상세 페이지에서 에피소드 목록을 보여주기 위해 Episode 와의 연관 포함
 * - 화면에서는 AnimeListDto / AnimeDetailDto 로 필요한 데이터만 노출
 */
@Entity
@Table(name = "anime") // NEW: dev 단계에서는 신규 통합 테이블로 생성
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Anime {

	@Id // 기본키
	@GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략
	private Long id; // 애니 고유 ID

	@Column(nullable = false, unique = true) // null 불허, 고유값
	private String title; // 애니 제목 (한글)

	@Column(nullable = true) // null 허용
	private String titleEn; // 애니 제목 (영어)

	@Column(nullable = true) // null 허용
	private String titleJp; // 애니 제목 (일본어)

	@Column(length = 500) // 길이 제한
	private String synopsis; // 간단한 줄거리 (목록용)

	@Column(columnDefinition = "TEXT") // 긴 텍스트 저장용
	private String fullSynopsis; // NEW 전체 줄거리 (상세용)

	@Column(nullable = false) // null 불허
	private String posterUrl; // 포스터 이미지 URL

	@Column(nullable = false) // null 불허
	private Integer totalEpisodes; // 총 에피소드 수

	@Enumerated(EnumType.STRING) // enum 을 문자열로 저장
	@Column(nullable = false)
	private com.ottproject.ottbackend.enums.AnimeStatus status; // 방영 상태 (방영중, 완결, 방영예정, 방영중단)

	@Column(nullable = false)
	private java.time.LocalDate releaseDate; // 방영 시작일

	@Column(nullable = true)
	private java.time.LocalDate endDate; // 방영 종료일 (완결된 경우)

	@Column(nullable = false)
	private String ageRating; // 연령 등급 (전체 이용가, 12세이상, 15세이상, 19세이상)

	@Column(nullable = false)
	private Double rating; // 평점 (0.0 ~ 5.0)

	@Column(nullable = false)
	private Integer ratingCount; // 평점을 준 사용자 수

	@Column(nullable = false)
	private Boolean isExclusive; // 라프텔 독점 여부

	@Column(nullable = false)
	private Boolean isNew; // 신작 여부

	@Column(nullable = false)
	private Boolean isPopular; // 인기작 여부

	@Column(nullable = false)
	private Boolean isCompleted; // 완결 여부

	@Column(nullable = false)
	private Boolean isSubtitle; // 자막 제공 여부

	@Column(nullable = false)
	private Boolean isDub; // 더빙 제공 여부

	@Column(nullable = false)
	private Boolean isSimulcast; // 동시방영 여부

	@Column(nullable = false)
	private String broadcastDay; // 방영 요일 (월,화,수,목,금,토,일)

	@Column(nullable = false)
	private String broadCastTime; // 방영 시간

	@Column(nullable = false)
	private String season; // 시즌 (봄, 여름, 가을, 겨울)

	@Column(nullable = false)
	private Integer year; // 방영 년도

	@Column(nullable = false)
	private String type; // 애니 타입 (TV, 영화, OVA, OAD 등)

	@Column(nullable = false)
	private Integer duration; // 에피소드당 러닝타임 (분)

	@Column(nullable = false)
	private String source; // 원작 (만화, 라이트노벨, 오리지널 등)

	@Column(nullable = false)
	private String country; // 제작국

	@Column(nullable = false)
	private String language; // 언어 (일본어, 한국어 등)

	// ===== 상세 추가 정보(기존 AnimeDetail) =====
	@Column(columnDefinition = "TEXT")
	private String voiceActors; // NEW 성우 정보 (JSON/Text 원문)

	@Column(nullable = true)
	private String director; // NEW 감독

	@Column(nullable = true)
	private String releaseQuarter; // NEW 출시 분기 (예: 2019년 3분기)

	@Column(nullable = false)
	@Builder.Default
	private Integer currentEpisodes = 0; // NEW 현재 업로드된 에피소드 수

	// ===== 관리 정보 =====
	@Column(nullable = false)
	@Builder.Default
	private Boolean isActive = true; // 활성화 여부

	@CreatedDate // 생성일시 자동 설정
	@Column(nullable = false)
	@Builder.Default
	private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now(); // NEW 생성일시

	@LastModifiedDate // 수정일시 자동 업데이트
	@Column(nullable = false)
	@Builder.Default
	private java.time.LocalDateTime updatedAt = java.time.LocalDateTime.now(); // NEW 수정일시

	// ===== 에피소드 연관 =====
	/**
	 * 하나의 Anime 는 여러 에피소드를 가짐
	 * 상세 페이지에서 에피소드 리스트를 바로 조회할 수 있도록 일대다 매핑 추가
	 */
	@OneToMany(mappedBy = "anime", cascade = CascadeType.ALL, fetch = FetchType.LAZY) // NEW
	@Builder.Default
	private java.util.List<Episode> episodes = new java.util.ArrayList<>(); // NEW 에피소드 목록

	// ===== 편의 메서드 =====
	public void addEpisode(Episode episode) { // NEW
		this.episodes.add(episode); // NEW
		episode.setAnime(this); // NEW
	} // NEW

	public void removeEpisode(Episode episode) { // NEW
		this.episodes.remove(episode); // NEW
		episode.setAnime(null); // NEW
	} // NEW
}