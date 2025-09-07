package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 애니 엔티티(목록+상세 통합)
 *
 * 큰 흐름
 * - 목록/상세에서 필요한 메타를 단일 테이블에 저장한다.
 * - 장르/태그/제작사와 다대다, 에피소드와 일대다 연관을 가진다.
 * - Auditing 으로 생성/수정 시각을 관리한다.
 *
 * 필드 개요
 * - id/title/titleEn/titleJp: 식별/다국어 제목
 * - synopsis/fullSynopsis: 요약/상세 줄거리
 * - status/releaseDate/endDate/year/season: 방영 상태/기간/분기/연도
 * - rating/ratingCount: 평점/투표수
 * - isExclusive/isNew/isPopular/isCompleted/isSubtitle/isDub/isSimulcast: 특성 플래그
 * - broadcastDay/broadCastTime/type/duration/source/country/language: 방송 메타
 * - voiceActors/director/releaseQuarter/currentEpisodes: 상세 메타
 * - isActive/createdAt/updatedAt: 운영/감사 정보
 */
@Entity
@Table(name = "anime")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Anime {

	@Id // 기본키
	@GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략
	private Long id; // 애니 고유 ID (DB에서 자동 생성)

	@Column(nullable = true, unique = true) // null 허용, 고유값
	private Long malId; // MyAnimeList ID (Jikan API 식별자)

	@Column(nullable = true, unique = true) // null 허용, 고유값
	private String title; // 애니 제목 (한글)

	@Column(nullable = true) // null 허용
	private String titleEn; // 애니 제목 (영어)

	@Column(nullable = true) // null 허용
	private String titleJp; // 애니 제목 (일본어)

	@Column(length = 500, nullable = true) // 길이 제한, null 허용
	private String synopsis; // 간단한 줄거리 (목록용)

	@Column(columnDefinition = "TEXT", nullable = true) // 긴 텍스트 저장용, null 허용
	private String fullSynopsis; // NEW 전체 줄거리 (상세용)

	@Column(nullable = true) // null 허용 (포스터가 없는 경우)
	private String posterUrl; // 포스터 이미지 URL

	@Column(nullable = true) // null 허용 (배경이미지가 없는 경우)
	private String backdropUrl; // 배경 이미지 URL (TMDB에서 제공)

	@Column(nullable = true) // null 허용 (방영 예정작은 에피소드 수를 모름)
	private Integer totalEpisodes; // 총 에피소드 수

	@Enumerated(EnumType.STRING) // enum 을 문자열로 저장
	@Column(nullable = false)
	private com.ottproject.ottbackend.enums.AnimeStatus status; // 방영 상태 (방영중, 완결, 방영예정, 방영중단)

	@Column(nullable = true) // null 허용 (방영 예정작은 정확한 날짜를 모름)
	private java.time.LocalDate releaseDate; // 방영 시작일

	@Column(nullable = true)
	private java.time.LocalDate endDate; // 방영 종료일 (완결된 경우)

	@Column(nullable = false)
	private String ageRating; // 연령 등급 (전체 이용가, 12세이상, 15세이상, 19세이상)

	@Column(nullable = true) // null 허용 (아직 평점이 없는 신작)
	private Double rating; // 평점 (0.0 ~ 5.0)

	@Column(nullable = true) // null 허용 (아직 평점이 없는 신작)
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

	@Column(nullable = true) // null 허용 (방영 예정작은 방영 요일을 모름)
	private String broadcastDay; // 방영 요일 (월,화,수,목,금,토,일)

	@Column(nullable = true) // null 허용 (방영 예정작은 방영 시간을 모름)
	private String broadCastTime; // 방영 시간

	@Column(nullable = true) // null 허용 (방영 예정작은 분기를 모름)
	private String season; // 시즌 (1분기, 2분기, 3분기, 4분기)

	@Column(nullable = true) // null 허용 (방영 예정작은 연도를 모름)
	private Integer year; // 방영 년도

	@Column(nullable = true) // null 허용 (정보가 없는 경우)
	private String type; // 애니 타입 (TV, 영화, OVA, OAD 등)

	@Column(nullable = true) // null 허용 (정보가 없는 경우)
	private Integer duration; // 에피소드당 러닝타임 (분)

	@Column(nullable = true) // null 허용 (원작 정보가 없는 경우)
	private String source; // 원작 (만화, 라이트노벨, 오리지널 등)

	@Column(nullable = true) // null 허용 (정보가 없는 경우)
	private String country; // 제작국

	@Column(nullable = true) // null 허용 (정보가 없는 경우)
	private String language; // 언어 (일본어, 한국어 등)

	// ===== 상세 추가 정보(기존 AnimeDetail) =====
	// String 필드들을 엔티티 관계로 변경

	@Column(nullable = true)
	private String releaseQuarter; // NEW 출시 분기 (예: 2019년 3분기)

	@Column(nullable = false)
	private Integer currentEpisodes = 0; // 현재 업로드된 에피소드 수

	// ===== 관리 정보 =====
	@Column(nullable = false)
	private Boolean isActive = true; // 활성화 여부

	@CreatedDate // 생성일시 자동 설정
	@Column(nullable = false)
	private java.time.LocalDateTime createdAt; // 생성일시

	@LastModifiedDate // 수정일시 자동 업데이트
	@Column(nullable = false)
	private java.time.LocalDateTime updatedAt; // 수정일시

	// ===== 에피소드 연관 =====
	/**
	 * 하나의 Anime 는 여러 에피소드를 가짐
	 * 상세 페이지에서 에피소드 리스트를 바로 조회할 수 있도록 일대다 매핑 추가
	 */
	@OneToMany(mappedBy = "anime", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private java.util.List<Episode> episodes = new java.util.ArrayList<>(); // 에피소드 목록

	// ===== 장르/태그/제작사 연관 =====
	@ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE}) // 장르 다대다
	@JoinTable(
			name = "anime_genres", // 조인 테이블
			joinColumns = @JoinColumn(name = "anime_id", referencedColumnName = "id"), // 현재 FK
			inverseJoinColumns = @JoinColumn(name = "genre_id", referencedColumnName = "id") // 대상 FK
	)
	private java.util.Set<Genre> genres = new java.util.HashSet<>(); // 장르 집합

	@ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE}) // 태그 다대다
	@JoinTable(
			name = "anime_tags", // 조인 테이블
			joinColumns = @JoinColumn(name = "anime_id", referencedColumnName = "id"), // 현재 FK (Anime에 기본키)
			inverseJoinColumns = @JoinColumn(name = "tag_id", referencedColumnName = "id") // 대상 FK (tag에 기본키)
	)
	private java.util.Set<Tag> tags = new java.util.HashSet<>(); // 태그 집합

	@ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE}) // 스튜디오 다대다
	@JoinTable(
			name = "anime_studios", // 조인 테이블
			joinColumns = @JoinColumn(name = "anime_id", referencedColumnName = "id"), // 현재 FK
			inverseJoinColumns = @JoinColumn(name = "studio_id", referencedColumnName = "id") // 대상 FK
	)
	private java.util.Set<Studio> studios = new java.util.HashSet<>(); // 스튜디오 집합

	@ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE}) // 감독 다대다
	@JoinTable(
			name = "anime_directors", // 조인 테이블
			joinColumns = @JoinColumn(name = "anime_id", referencedColumnName = "id"), // 현재 FK
			inverseJoinColumns = @JoinColumn(name = "director_id", referencedColumnName = "id") // 대상 FK
	)
	private java.util.Set<Director> directors = new java.util.HashSet<>(); // 감독 집합

	@ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE}) // 성우 다대다
	@JoinTable(
			name = "anime_voice_actors", // 조인 테이블
			joinColumns = @JoinColumn(name = "anime_id", referencedColumnName = "id"), // 현재 FK
			inverseJoinColumns = @JoinColumn(name = "voice_actor_id", referencedColumnName = "id") // 대상 FK
	)
	private java.util.Set<VoiceActor> voiceActors = new java.util.HashSet<>(); // 성우 집합

	@ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE}) // 캐릭터 다대다
	@JoinTable(
			name = "anime_characters", // 조인 테이블
			joinColumns = @JoinColumn(name = "anime_id", referencedColumnName = "id"), // 현재 FK
			inverseJoinColumns = @JoinColumn(name = "character_id", referencedColumnName = "id") // 대상 FK
	)
	private java.util.Set<Character> characters = new java.util.HashSet<>(); // 캐릭터 집합

	// ===== 편의 메서드 =====
	public void addEpisode(Episode episode) { // NEW
		this.episodes.add(episode); // NEW
		episode.setAnime(this); // NEW
	} // NEW

	public void removeEpisode(Episode episode) { // NEW
		this.episodes.remove(episode); // NEW
		episode.setAnime(null); // NEW
	} // NEW
	
	// ===== Getter/Setter 메서드 =====
	public java.util.List<Episode> getEpisodes() {
		return episodes;
	}
	
	public void setEpisodes(java.util.List<Episode> episodes) {
		this.episodes = episodes;
	}
	
	public java.util.Set<Genre> getGenres() {
		return genres;
	}
	
	public void setGenres(java.util.Set<Genre> genres) {
		this.genres = genres;
	}
	
	public java.util.Set<Tag> getTags() {
		return tags;
	}
	
	public void setTags(java.util.Set<Tag> tags) {
		this.tags = tags;
	}
	
	public java.util.Set<Studio> getStudios() {
		return studios;
	}
	
	public void setStudios(java.util.Set<Studio> studios) {
		this.studios = studios;
	}
	
	public java.util.Set<Director> getDirectors() {
		return directors;
	}
	
	public void setDirectors(java.util.Set<Director> directors) {
		this.directors = directors;
	}
	
	public java.util.Set<VoiceActor> getVoiceActors() {
		return voiceActors;
	}
	
	public void setVoiceActors(java.util.Set<VoiceActor> voiceActors) {
		this.voiceActors = voiceActors;
	}
	
	public java.util.Set<Character> getCharacters() {
		return characters;
	}
	
	public void setCharacters(java.util.Set<Character> characters) {
		this.characters = characters;
	}

	public void addDirector(Director director) {
		this.directors.add(director);
		director.getAnimes().add(this);
	}

	public void removeDirector(Director director) {
		this.directors.remove(director);
		director.getAnimes().remove(this);
	}

	public void addVoiceActor(VoiceActor voiceActor) {
		this.voiceActors.add(voiceActor);
		voiceActor.getAnimes().add(this);
	}

	public void removeVoiceActor(VoiceActor voiceActor) {
		this.voiceActors.remove(voiceActor);
		voiceActor.getAnimes().remove(this);
	}

	public void addCharacter(Character character) {
		this.characters.add(character);
		character.getAnimes().add(this);
	}

	public void removeCharacter(Character character) {
		this.characters.remove(character);
		character.getAnimes().remove(this);
	}

	// ===== 정적 팩토리 메서드 =====
	/**
	 * 애니메이션 생성 (비즈니스 로직 캡슐화)
	 * 
	 * @param title 애니메이션 제목 (필수)
	 * @param titleEn 영어 제목 (선택)
	 * @param titleJp 일본어 제목 (선택)
	 * @param synopsis 간단한 줄거리 (선택)
	 * @param fullSynopsis 전체 줄거리 (선택)
	 * @param posterUrl 포스터 URL (선택)
	 * @param totalEpisodes 총 에피소드 수 (선택)
	 * @param status 방영 상태 (필수)
	 * @param releaseDate 방영 시작일 (선택)
	 * @param endDate 방영 종료일 (선택)
	 * @param ageRating 연령 등급 (필수)
	 * @param rating 평점 (선택)
	 * @param ratingCount 평점 투표수 (선택)
	 * @param isExclusive 독점 여부 (필수)
	 * @param isNew 신작 여부 (필수)
	 * @param isPopular 인기작 여부 (필수)
	 * @param isCompleted 완결 여부 (필수)
	 * @param isSubtitle 자막 제공 여부 (필수)
	 * @param isDub 더빙 제공 여부 (필수)
	 * @param isSimulcast 동시방영 여부 (필수)
	 * @param broadcastDay 방영 요일 (선택)
	 * @param broadCastTime 방영 시간 (선택)
	 * @param season 시즌 (선택)
	 * @param year 방영 년도 (선택)
	 * @param type 애니메이션 타입 (선택)
	 * @param duration 에피소드당 러닝타임 (선택)
	 * @param source 원작 (선택)
	 * @param country 제작국 (선택)
	 * @param language 언어 (선택)
	 * @param releaseQuarter 출시 분기 (선택)
	 * @param currentEpisodes 현재 업로드된 에피소드 수 (선택, 기본값: 0)
	 * @return 생성된 Anime 엔티티
	 */
	public static Anime createAnime(
			Long malId, String title, String titleEn, String titleJp,
			String synopsis, String fullSynopsis, String posterUrl, Integer totalEpisodes,
			com.ottproject.ottbackend.enums.AnimeStatus status,
			java.time.LocalDate releaseDate, java.time.LocalDate endDate,
			String ageRating, Double rating, Integer ratingCount,
			Boolean isExclusive, Boolean isNew, Boolean isPopular, Boolean isCompleted,
			Boolean isSubtitle, Boolean isDub, Boolean isSimulcast,
			String broadcastDay, String broadCastTime, String season, Integer year,
			String type, Integer duration, String source, String country, String language,
			String releaseQuarter, Integer currentEpisodes) {
		
		// 필수 필드 검증 (title은 이제 선택사항)
		// title이 null이거나 빈 문자열이면 null로 처리
		if (status == null) {
			throw new IllegalArgumentException("방영 상태는 필수입니다.");
		}
		if (ageRating == null || ageRating.trim().isEmpty()) {
			throw new IllegalArgumentException("연령 등급은 필수입니다.");
		}
		if (isExclusive == null || isNew == null || isPopular == null || 
			isCompleted == null || isSubtitle == null || isDub == null || isSimulcast == null) {
			throw new IllegalArgumentException("플래그 필드들은 필수입니다.");
		}
		
		// Anime 엔티티 생성
		Anime anime = new Anime();
		
		// 기본 정보 설정
		anime.malId = malId;
		anime.title = (title != null && !title.trim().isEmpty()) ? title.trim() : null;
		anime.titleEn = titleEn != null ? titleEn.trim() : null;
		anime.titleJp = titleJp != null ? titleJp.trim() : null;
		anime.synopsis = synopsis != null ? synopsis.trim() : "";
		anime.fullSynopsis = fullSynopsis != null ? fullSynopsis.trim() : "";
		anime.posterUrl = posterUrl != null ? posterUrl.trim() : null;
		anime.totalEpisodes = totalEpisodes;
		anime.status = status;
		anime.releaseDate = releaseDate;
		anime.endDate = endDate;
		anime.ageRating = ageRating.trim();
		anime.rating = rating;
		anime.ratingCount = ratingCount;
		
		// 플래그 설정
		anime.isExclusive = isExclusive;
		anime.isNew = isNew;
		anime.isPopular = isPopular;
		anime.isCompleted = isCompleted;
		anime.isSubtitle = isSubtitle;
		anime.isDub = isDub;
		anime.isSimulcast = isSimulcast;
		
		// 방송 정보 설정
		anime.broadcastDay = broadcastDay != null ? broadcastDay.trim() : null;
		anime.broadCastTime = broadCastTime != null ? broadCastTime.trim() : null;
		anime.season = season != null ? season.trim() : null;
		anime.year = year;
		anime.type = type != null ? type.trim() : null;
		anime.duration = duration;
		anime.source = source != null ? source.trim() : null;
		anime.country = country != null ? country.trim() : "일본";
		anime.language = language != null ? language.trim() : "일본어";
		anime.releaseQuarter = releaseQuarter != null ? releaseQuarter.trim() : null;
		
		// 기본값 설정
		anime.currentEpisodes = currentEpisodes != null ? currentEpisodes : 0;
		anime.isActive = true;
		
		// Auditing 필드는 JPA가 자동 설정
		anime.createdAt = java.time.LocalDateTime.now();
		anime.updatedAt = java.time.LocalDateTime.now();
		
		return anime;
	}
}