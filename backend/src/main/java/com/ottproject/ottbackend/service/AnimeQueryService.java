package com.ottproject.ottbackend.service; // 패키지 선언

import com.ottproject.ottbackend.dto.AniDetailDto;
import com.ottproject.ottbackend.dto.AniListDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.mybatis.AniQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 애니 조회 서비스(MyBatis)
 * - 목록(필터/정렬/페이지) 및 상세(에피소드/장르/제작사) 조회
 */
@RequiredArgsConstructor // 생성자 주입을 자동 생성(final 필드 대상)
@Service // 스프링 서비스 컴포넌트로 등록
@Transactional(readOnly = true) // 기본적으로 읽기 전용 트랜잭션으로 실행
public class AniQueryService { // 애니 조회 관련 비즈니스 로직 제공
	private final AniQueryMapper mapper; // MyBatis 매퍼 의존성

	// 단일 genreId → 다중 genreIds 지원 + AND 개수(genreCount) 계산 후 전달, 태그 OR 필터(tagIds) 지원
	public PagedResponse<AniListDto> list( // 목록 조회 + 페이징 응답
				AnimeStatus status, List<Long> genreIds, Double minRating, Integer year, // 상태/장르/최소평점/연도 필터
				Boolean isDub, Boolean isSubtitle, Boolean isExclusive, Boolean isCompleted, // 옵션 배지 필터
				Boolean isNew, Boolean isPopular, String sort, int page, int size, // 정렬/페이지 파라미터
				List<Long> tagIds // 태그 OR 필터
	) { // 목록 메서드 시작
		int limit = size; // LIMIT 값 계산(페이지 크기)
		int offset = Math.max(page, 0) * size; // OFFSET 계산(0 미만 방지)

		// 장르 ID 입력값 정리: null 제거 + 중복 제거 → 비어 있으면 null 처리(필터 미적용)
		java.util.List<Long> distinctGenreIds = null; // 정제된 장르 ID 목록 초기값
		if (genreIds != null) { // 장르 파라미터가 전달된 경우에만 처리
			distinctGenreIds = genreIds.stream() // 스트림 생성
				.filter(java.util.Objects::nonNull) // null 값 제거
				.distinct() // 중복 제거
				.collect(java.util.stream.Collectors.toList()); // 리스트로 수집
			if (distinctGenreIds.isEmpty()) { // 정제 결과가 비어있으면
				distinctGenreIds = null; // 필터 비적용으로 간주
			}
		}
		Integer genreCount = (distinctGenreIds == null) ? 0 : distinctGenreIds.size(); // AND 매칭용 선택 장르 개수

		// 태그 ID 입력값 정리: null 제거 + 중복 제거 → 비어 있으면 null 처리(필터 미적용)
		java.util.List<Long> distinctTagIds = null; // 정제된 태그 ID 목록 초기값
		if (tagIds != null) { // 태그 파라미터가 전달된 경우에만 처리
			distinctTagIds = tagIds.stream()
				.filter(java.util.Objects::nonNull)
				.distinct()
				.collect(java.util.stream.Collectors.toList());
			if (distinctTagIds.isEmpty()) {
				distinctTagIds = null; // 필터 비적용으로 간주
			}
		}

		java.util.List<AniListDto> items = mapper.findAniList( // 목록 데이터 조회
					status, distinctGenreIds, genreCount, distinctTagIds, minRating, year, isDub, isSubtitle, isExclusive,
					isCompleted, isNew, isPopular, sort, limit, offset
		); // 조회된 목록 아이템들

		long total = mapper.countAniList( // 총 개수 조회(페이지네이션 total)
					status, distinctGenreIds, genreCount, distinctTagIds, minRating, year, isDub, isSubtitle, isExclusive,
					isCompleted, isNew, isPopular
		); // 조건 동일한 COUNT(1)

		return new PagedResponse<>(items, total, page, size); // 표준 페이지 응답으로 래핑하여 반환
	}

	public AniDetailDto detail(long aniId) { // 상세 조회(비로그인/찜여부 제외)
		AniDetailDto dto = mapper.findAniDetailByAniId(aniId); // 상세 헤더/배지 등 기본 정보 조회
		if (dto == null) return null; // 대상 없으면 null 반환
		dto.setEpisodes(mapper.findEpisodesByAniId(aniId)); // 에피소드 리스트 채우기
		dto.setGenres(mapper.findGenresByAniId(aniId)); // 장르 리스트 채우기
		dto.setStudios(mapper.findStudiosByAniId(aniId)); // 제작사 리스트 채우기
		return dto; // 완성된 DTO 반환
	}

	public AniDetailDto detail(long aniId, Long currentUserId) { // 상세 조회(로그인 포함/찜여부 포함)
		AniDetailDto dto = mapper.findAniDetailByAniIdWithUser( // 찜 여부 계산 포함 상세 조회
					aniId, // 대상 애니 ID
					currentUserId // 현재 사용자 ID(비로그인 시 null)
		); // DB 조회 실행
		if (dto == null) return null; // 대상 없으면 null 반환
		dto.setEpisodes(mapper.findEpisodesByAniId(aniId)); // 에피소드 리스트 채우기
		dto.setGenres(mapper.findGenresByAniId(aniId)); // 장르 리스트 채우기
		dto.setStudios(mapper.findStudiosByAniId(aniId)); // 제작사 리스트 채우기
		return dto; // 완성된 DTO 반환
	}
}