package com.ottproject.ottbackend.service;

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
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성 -> 의존성 주입
@Service
@Transactional(readOnly = true) // 읽기 전용 트랜잭션 (플러시/쓰기 비활성화로 성능 향상)
public class AniQueryService { // 읽기(조회) 전용 서비스
	private final AniQueryMapper mapper; // MyBatis 매퍼 의존성 주입

	public PagedResponse<AniListDto> list( // 목록 조회 + 페이징 응답 래핑
				AnimeStatus status, Long genreId, Double minRating, Integer year, // 필터: 상태/장르/최소평점/연도
				Boolean isDub, Boolean isSubtitle, Boolean isExclusive, Boolean isCompleted, // 필터: 배지/노출
				Boolean isNew, Boolean isPopular, String sort, int page, int size // 정렬/페이지/사이즈
	) {
		int limit = size; // LIMIT 값 계산
		int offset = Math.max(page, 0) * size; // OFFSET 계산(0-base 보호)
		java.util.List<AniListDto> items = mapper.findAniList( // 목록 데이터 조회
					status, genreId, minRating, year, isDub, isSubtitle, isExclusive,
					isCompleted, isNew, isPopular, sort, limit, offset
		);
		long total = mapper.countAniList( // 총 개수 조회(페이지네이션용)
					status, genreId, minRating, year, isDub, isSubtitle, isExclusive,
					isCompleted, isNew, isPopular
		);
		return new PagedResponse<>(items, total, page, size); // 표준 페이지 응답으로 래핑
	}

	public AniDetailDto detail(long aniId) { // 기존 시그니처 유지(호환)
		AniDetailDto dto = mapper.findAniDetailByAniId(aniId); // 사용자 없이 상세 조회
		if (dto == null) return null; // 없으면 null
		dto.setEpisodes(mapper.findEpisodesByAniId(aniId)); // 에피소드
		dto.setGenres(mapper.findGenresByAniId(aniId)); // 장르
		dto.setStudios(mapper.findStudiosByAniId(aniId)); // 제작사
		return dto; // 반환
	}

	public AniDetailDto detail(long aniId, Long currentUserId) { // 사용자 포함 상세 조회(찜 여부 포함)
		AniDetailDto dto = mapper.findAniDetailByAniIdWithUser( // MyBatis 에서 isFavorited 까지 계산
					aniId, // 애니 ID
					currentUserId // 현재 사용자 ID(비로그인 null)
		); // 상세 + 찜 여부 단건 조회
		if (dto == null) return null; // 대상 없음 보호
		dto.setEpisodes(mapper.findEpisodesByAniId(aniId)); // 에피소드 리스트 세팅
		dto.setGenres(mapper.findGenresByAniId(aniId)); // 장르 리스트 세팅
		dto.setStudios(mapper.findStudiosByAniId(aniId)); // 제작사 리스트 세팅
		return dto; // 완성된 DTO 반환
	}
}
