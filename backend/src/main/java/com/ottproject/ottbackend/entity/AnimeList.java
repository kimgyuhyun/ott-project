package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 애니메이션 목록에 보여줄 기본 정보를 저장하는 엔티티
 * 라프텔의 애니 목록 페이지 기반 설계
 * 목록 조회 시 성능 최적화를 위해 상세 정보와 분리
 */
@Entity
@Table(name = "ani_list")
@Getter
@Setter
@Builder
@NoArgsConstructor // 기본 생성자 생성
@AllArgsConstructor // 모든 필드 생성자 생성
@EntityListeners(AuditingEntityListener.class) // JPA Auditing 기능 활성화
public class AniList {
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

	@Column(nullable = false)
	private Boolean isActive; // 활성화 여부

	@CreatedDate // 생성일시 자동 설정
	@Column(nullable = false)
	@Builder.Default // 빌더 패턴에서 기본값 설정
	private java.time.LocalDateTime updatedAt = java.time.LocalDateTime.now(); // 생성일시

	// ===== 연관관계 매핑 =====
	/**
	 * 장르와의 다대다 관계
	 * 중간 테이블(ani_list_genres)을 통해 관계 관리
	 */
	@ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE}) // 지연 로딩, 영속성 전이 설정
	@JoinTable(
			name = "ani_list_genres", // 중간 테이블명
			joinColumns = @JoinColumn(name = "ani_list_id", referencedColumnName = "id"), // 현재 엔티티(AniList)의 외래키
			inverseJoinColumns = @JoinColumn(name = "genre_id", referencedColumnName = "id" ), // 연관 엔티티(Genre)의 외래키
			uniqueConstraints = @UniqueConstraint(columnNames = {"ani_list_id", "genre_id"}) // 복합 유니크 제약 조건
	)
	@Builder.Default // 빌더 패턴에서 기본값 설정
	private java.util.Set<Genre> genres = new java.util.HashSet<>(); // 장르 목록 (Set 으로 중복 방지)

	/**
	 * 태그와의 다대다 관계(정규화 테이블: ani_list_tags)
	 * - 장르와 동일한 패턴으로 JoinTable 정의
	 */
	@ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
	@JoinTable(
			name = "ani_list_tags",
			joinColumns = @JoinColumn(name = "ani_list_id", referencedColumnName = "id"),
			inverseJoinColumns = @JoinColumn(name = "tag_id", referencedColumnName = "id"),
			uniqueConstraints = @UniqueConstraint(columnNames = {"ani_list_id", "tag_id"})
	)
	@Builder.Default
	private java.util.Set<Tag> tags = new java.util.HashSet<>(); // 태그 목록

	/**
	 * 제작사와의 다대다 관계
	 * 중간 테이블(ani_list_studios)을 통해 관계 관리
	 */
	@ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE}) // 지연 로딩, 영속성 전이 설정
	@JoinTable(
			name = "ani_list_studios", // 중간 테이블명
			joinColumns = @JoinColumn(name = "ani_list_id", referencedColumnName = "id"), // 현재 엔티티(AniList)의 외래키
			inverseJoinColumns = @JoinColumn(name = "studio_id", referencedColumnName = "id"), // 연관 엔티티(Studio)의 외래키
			uniqueConstraints = @UniqueConstraint(columnNames = {"ani_list_id", "studio_id"}) // 복합 유니크 제약조건
	)
	@Builder.Default // 빌더 패턴에서 기본값 설정
	private java.util.Set<Studio> studios = new java.util.HashSet<>(); // 제작사 목록 (Set 으로 중복 방지)

	/**
	 * AniDetail 과의 일대일 관계
	 * 하나의 AniList 는 하나의 AniDetail 을 가짐 (상세 정보)
	 */
	@OneToOne(mappedBy = "aniList", cascade = CascadeType.ALL, fetch = FetchType.LAZY) // 일대일 관계, cascade 연쇄, 지연 로딩
	private AniDetail aniDetail; // 상세 정보

	// ===== 편의 메서드 =====
	/**
	 * 장르 추가 메서드
	 * @param genre 추가할 장르
	 */
	public void addGenre(Genre genre) {
		this.genres.add(genre);
		genre.getAniLists().add(this); // 양방향 관계 설정
	}

	/**
	 * 장르 제거 메서드
	 * @param genre 제거할 장르
	 */
	public void removeGenre(Genre genre) {
		this.genres.remove(genre);
		genre.getAniLists().remove(this); // 양방향 관계 해제
	}

	/**
	 * 제작사 추가 메서드
	 * @param studio 추가할 제작사
	 */
	public void addStudio(Studio studio) {
		this.studios.add(studio);
		studio.getAniLists().add(this); // 양방향 관계 설정
	}

	/**
	 * 제작사 제거 메서드
	 * @param studio 제거할 제작사
	 */
	public void removeStudio(Studio studio) {
		this.studios.remove(studio);
		studio.getAniLists().remove(this); // 양방향 관계 해제
	}

}
