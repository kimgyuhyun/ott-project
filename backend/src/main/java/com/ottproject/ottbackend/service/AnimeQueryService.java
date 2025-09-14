package com.ottproject.ottbackend.service; // 패키지 선언

import com.ottproject.ottbackend.dto.AnimeDetailDto;
import com.ottproject.ottbackend.dto.AnimeListDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.mybatis.AnimeQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AnimeQueryService
 *
 * 큰 흐름
 * - 목록/상세/연관 데이터를 읽기 전용으로 조회하는 서비스(MyBatis 연동).
 *
 * 메서드 개요
 * - list: 필터/정렬/페이지를 적용한 목록 조회(AND/OR 필터 정제 포함)
 * - detail(aniId): 상세(에피소드/장르/제작사)
 * - detail(aniId, currentUserId): 로그인 사용자의 찜 여부 포함 상세
 */
@RequiredArgsConstructor // 생성자 주입을 자동 생성(final 필드 대상)
@Service // 스프링 서비스 컴포넌트로 등록
@Transactional(readOnly = true) // 기본적으로 읽기 전용 트랜잭션으로 실행
public class AnimeQueryService { // 애니 조회 관련 비즈니스 로직 제공
	private final AnimeQueryMapper mapper; // MyBatis 매퍼 의존성

	// 단일 genreId → 다중 genreIds 지원 + AND 개수(genreCount) 계산 후 전달, 태그 OR 필터(tagIds) 지원
	public PagedResponse<AnimeListDto> list( // 목록 조회 + 페이징 응답
											 AnimeStatus status, List<Long> genreIds, Double minRating, Integer year, Integer quarter, // 상태/장르/최소평점/연도/분기 필터
											 String type, // 출시 타입 필터
											 Boolean isDub, Boolean isSubtitle, Boolean isExclusive, Boolean isCompleted, // 옵션 배지 필터
											 Boolean isNew, Boolean isPopular, String sort, int page, int size, // 정렬/페이지 파라미터
											 List<Long> tagIds // 태그 OR 필터
	) { // 목록 메서드 시작
		int limit = size; // LIMIT 값 계산(페이지 크기)
		int offset = Math.max(page, 0) * size; // OFFSET 계산(0 미만 방지)

		// 장르 ID 입력값 정리: null 제거 + 중복 제거 → 비어 있으면 null 처리(필터 미적용)
		java.util.List<Long> distinctGenreIds = null; // 정제된 장르 ID 목록 초기값
		if (genreIds != null && !genreIds.isEmpty()) { // 장르 파라미터가 전달된 경우에만 처리
			distinctGenreIds = genreIds.stream() // 스트림 생성
				.filter(java.util.Objects::nonNull) // null 값 제거
				.distinct() // 중복 제거
				.collect(java.util.stream.Collectors.toList()); // 리스트로 수집
			if (distinctGenreIds.isEmpty()) { // 정제 결과가 비어있으면
				distinctGenreIds = null; // 필터 비적용으로 간주
			}
		}
		Integer genreCount = (distinctGenreIds == null || distinctGenreIds.isEmpty()) ? null : distinctGenreIds.size(); // AND 매칭용 선택 장르 개수

		// 태그 ID 입력값 정리: null 제거 + 중복 제거 → 비어 있으면 null 처리(필터 미적용)
		java.util.List<Long> distinctTagIds = null; // 정제된 태그 ID 목록 초기값
		if (tagIds != null && !tagIds.isEmpty()) { // 태그 파라미터가 전달된 경우에만 처리
			distinctTagIds = tagIds.stream()
				.filter(java.util.Objects::nonNull)
				.distinct()
				.collect(java.util.stream.Collectors.toList());
			if (distinctTagIds.isEmpty()) {
				distinctTagIds = null; // 필터 비적용으로 간주
			}
		}

		java.util.List<AnimeListDto> items = mapper.findAniList( // 목록 데이터 조회
						status, distinctGenreIds, genreCount, distinctTagIds, minRating, year, quarter, type, isDub, isSubtitle, isExclusive,
						isCompleted, isNew, isPopular, sort, limit, offset,
						null, null, null
		); // 조회된 목록 아이템들

		long total = mapper.countAniList( // 총 개수 조회(페이지네이션 total)
						status, distinctGenreIds, genreCount, distinctTagIds, minRating, year, quarter, type, isDub, isSubtitle, isExclusive,
						isCompleted, isNew, isPopular
		); // 조건 동일한 COUNT(1)

		return new PagedResponse<>(items, total, page, size); // 표준 페이지 응답으로 래핑하여 반환
	}

	public AnimeDetailDto detail(long aniId) { // 상세 조회(비로그인/찜여부 제외)
		AnimeDetailDto dto = mapper.findAniDetailByAniId(aniId); // 상세 헤더/배지 등 기본 정보 조회
		if (dto == null) return null; // 대상 없으면 null 반환
		dto.setEpisodes(mapper.findEpisodesByAniId(aniId)); // 에피소드 리스트 채우기
		dto.setGenres(mapper.findGenresByAniId(aniId)); // 장르 리스트 채우기
		dto.setStudios(mapper.findStudiosByAniId(aniId)); // 제작사 리스트 채우기
		
		// 추가 정보 조회 (더보기 모달용)
		dto.setTags(mapper.findTagNamesByAniId(aniId)); // 태그 리스트 채우기
		dto.setVoiceActors(mapper.findVoiceActorsByAniId(aniId)); // 성우 리스트 채우기
		dto.setDirector(mapper.findDirectorsByAniId(aniId)); // 감독 리스트 채우기
		
		return dto; // 완성된 DTO 반환
	}

	public AnimeDetailDto detail(long aniId, Long currentUserId) { // 상세 조회(로그인 포함/찜여부 포함)
		AnimeDetailDto dto = mapper.findAniDetailByAniIdWithUser( // 찜 여부 계산 포함 상세 조회
						aniId, // 대상 애니 ID
						currentUserId // 현재 사용자 ID(비로그인 시 null)
		); // DB 조회 실행
		if (dto == null) return null; // 대상 없으면 null 반환
		
		// 디버깅 로그 추가
		System.out.println("🔍 [BACKEND] 애니메이션 상세 데이터 조회 결과:");
		System.out.println("  - aniId: " + dto.getAniId());
		System.out.println("  - title: " + dto.getTitle());
		System.out.println("  - isDub: " + dto.getIsDub() + " (type: " + (dto.getIsDub() != null ? dto.getIsDub().getClass().getSimpleName() : "null") + ")");
		System.out.println("  - isSubtitle: " + dto.getIsSubtitle() + " (type: " + (dto.getIsSubtitle() != null ? dto.getIsSubtitle().getClass().getSimpleName() : "null") + ")");
		
		// 에피소드 리스트 조회 및 디버깅
		var episodes = mapper.findEpisodesByAniId(aniId);
		System.out.println("🔍 [BACKEND] 에피소드 데이터 조회 결과:");
		if (episodes != null && !episodes.isEmpty()) {
			episodes.forEach(ep -> {
				System.out.println("  - episodeId: " + ep.getId() + ", episodeNumber: " + ep.getEpisodeNumber() + ", title: " + ep.getTitle());
			});
		} else {
			System.out.println("  - 에피소드 데이터 없음");
		}
		dto.setEpisodes(episodes); // 에피소드 리스트 채우기
		dto.setGenres(mapper.findGenresByAniId(aniId)); // 장르 리스트 채우기
		dto.setStudios(mapper.findStudiosByAniId(aniId)); // 제작사 리스트 채우기
		
		// 추가 정보 조회 (더보기 모달용)
		dto.setTags(mapper.findTagNamesByAniId(aniId)); // 태그 리스트 채우기
		dto.setVoiceActors(mapper.findVoiceActorsByAniId(aniId)); // 성우 리스트 채우기
		dto.setDirector(mapper.findDirectorsByAniId(aniId)); // 감독 리스트 채우기
		
		return dto; // 완성된 DTO 반환
	}

	public java.util.List<AnimeListDto> getWeeklyByDay(String day, int limit) {
		return mapper.findWeeklyByDay(day, limit);
	}

	public java.util.List<com.ottproject.ottbackend.dto.GenreSimpleDto> getAllGenres() {
		return mapper.findAllGenres();
	}

	public java.util.List<com.ottproject.ottbackend.dto.TagSimpleDto> getAllTags() {
		return mapper.findAllTags();
	}

	public java.util.List<String> getAllSeasons() {
		return mapper.findAllSeasons();
	}

	public java.util.List<com.ottproject.ottbackend.dto.StatusOptionDto> getAllStatuses() {
		return mapper.findAllStatuses();
	}

	public java.util.List<com.ottproject.ottbackend.dto.TypeOptionDto> getAllTypes() {
		return mapper.findAllTypes();
	}

	public java.util.List<com.ottproject.ottbackend.dto.YearOptionDto> getYearOptions() {
		return mapper.findYearOptions();
	}

	/**
	 * ID 목록으로 카드 리스트 조회 (입력 ID 순서 보존)
	 */
	public java.util.List<AnimeListDto> listByIds(java.util.List<Long> ids) {
		if (ids == null || ids.isEmpty()) return java.util.List.of();
		java.util.List<AnimeListDto> rows = mapper.findAniListByIds(ids);
		if (rows == null || rows.isEmpty()) return java.util.List.of();
		// 입력 순서 보존 정렬
		java.util.Map<Long, Integer> order = new java.util.HashMap<>();
		for (int i = 0; i < ids.size(); i++) order.put(ids.get(i), i);
		rows.sort((a, b) -> Integer.compare(order.getOrDefault(a.getAniId(), Integer.MAX_VALUE), order.getOrDefault(b.getAniId(), Integer.MAX_VALUE)));
		return rows;
	}
}