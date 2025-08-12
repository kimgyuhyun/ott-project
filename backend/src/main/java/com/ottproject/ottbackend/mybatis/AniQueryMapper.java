package com.ottproject.ottbackend.mybatis;

import com.ottproject.ottbackend.dto.*;
import com.ottproject.ottbackend.enums.AnimeStatus;
import org.apache.ibatis.annotations.Mapper; // MyBatis 매퍼 애노테이션
import org.apache.ibatis.annotations.Param;  // MyBatis 파라미터 바인딩

import java.util.List;

/**
 * 읽기 전용(MyBatis) 매퍼
 * 목록/상세.연관(에피소드, 장르, 제작사) 조회
 */
@Mapper
public interface AniQueryMapper { // 목록 상세/연관 조회 정의

    // 목록 조회
    List<AniListDto> findAniList( // 목록 조회 // 카드 그리드 데이터
            @Param("status") AnimeStatus status, // 상태 필터(ENUM) // DB 문자열과 매칭
            @Param("genreId") Long genreId, // 장르 필터 // 조인 사용
            @Param("minRating") Double minRating, // 최소 평점
            @Param("year") Integer year, // 방영 연도
            @Param("isDub") Boolean isDub, // 더빙 여부
            @Param("isSubtitle") Boolean isSubtitle, // 자막 여부
            @Param("isExclusive") Boolean isExclusive, // 독점 여부
            @Param("isCompleted") Boolean isCompleted, // 완결 여부
            @Param("isNew") Boolean isNew, // 신작 여부
            @Param("isPopular") Boolean isPopular, // 인기 여부
            @Param("sort") String sort, // 정렬 키(rating/year/popular/id)
            @Param("limit") int limit, // 페이지 크기
            @Param("offset") int offset // 오프셋
    );

    long countAniList( // 목록 총 개수 // 페이지네이션용
            @Param("status") AnimeStatus status, // 위와 동일 필터들
            @Param("genreId") Long genreId,
            @Param("minRating") Double minRating,
            @Param("year") Integer year,
            @Param("isDub") Boolean isDub,
            @Param("isSubtitle") Boolean isSubtitle,
            @Param("isExclusive") Boolean isExclusive,
            @Param("isCompleted") Boolean isCompleted,
            @Param("isNew") Boolean isNew,
            @Param("isPopular") Boolean isPopular
    );

    AniDetailDto findAniDetailByAniId(@Param("aniId") Long aniId); // 상세 헤더/더보기 영역 조회(aniId 기준)

    List<EpisodeDto> findEpisodesByAniId(@Param("aniId") Long aniId); // 상세: 에피소드 리스트
    List<GenreSimpleDto> findGenresByAniId(@Param("aniId") Long aniId); // 상세: 장르 리스트
    List<StudioSimpleDto> findStudiosByAniId(@Param("aniId") Long aniId); // 상세: 제작사 리스트
}


