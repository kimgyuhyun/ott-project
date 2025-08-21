package com.ottproject.ottbackend.mybatis;

import com.ottproject.ottbackend.dto.*;
import com.ottproject.ottbackend.enums.AnimeStatus;
import org.apache.ibatis.annotations.Mapper; // MyBatis 매퍼 애노테이션
import org.apache.ibatis.annotations.Param;  // MyBatis 파라미터 바인딩

import java.util.List;

/**
 * AnimeQueryMapper
 *
 * 큰 흐름
 * - 목록/상세 및 연관(에피소드/장르/제작사/태그) 조회를 담당하는 읽기 전용 MyBatis 매퍼.
 *
 * 메서드 개요
 * - findAniList/countAniList: 목록/총 개수(필터/정렬/페이지)
 * - findTagsByAniId: 상세 태그 목록
 * - findAniDetailByAniId/findAniDetailByAniIdWithUser: 상세(사용자 찜 여부 포함 가능)
 * - findEpisodesByAniId/findGenresByAniId/findStudiosByAniId: 상세 연관 목록
 */
@Mapper
public interface AnimeQueryMapper { // 목록 상세/연관 조회 정의

    // 목록 조회
    List<AnimeListDto> findAniList( // 목록 조회 // 카드 그리드 데이터 (anime 테이블 기준)
            @Param("status") AnimeStatus status, // 상태 필터(ENUM) // DB 문자열과 매칭
            @Param("genreIds") List<Long> genreIds, // 장르 다중 AND 필터
            @Param("genreCount") Integer genreCount, // AND 개수 매핑용
            @Param("tagIds") List<Long> tagIds, // 태그 OR 필터
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
            @Param("genreIds") List<Long> genreIds, // 장르 다중 AND 필터
            @Param("genreCount") Integer genreCount, // AND 개수 매핑용
            @Param("tagIds") List<Long> tagIds, // 태그 OR 필터
            @Param("minRating") Double minRating,
            @Param("year") Integer year,
            @Param("isDub") Boolean isDub,
            @Param("isSubtitle") Boolean isSubtitle,
            @Param("isExclusive") Boolean isExclusive,
            @Param("isCompleted") Boolean isCompleted,
            @Param("isNew") Boolean isNew,
            @Param("isPopular") Boolean isPopular
    );

    // 상세: 태그 목록 조회(선택)
    java.util.List<TagSimpleDto> findTagsByAniId(@Param("aniId") Long aniId);

    AnimeDetailDto findAniDetailByAniId(@Param("aniId") Long aniId); // 상세 헤더/더보기 영역 조회(aniId 기준, anime 기준)
    AnimeDetailDto findAniDetailByAniIdWithUser( // 사용자 포함 상세 조회 메서드 시그니처
            @Param("aniId") Long aniId, // 조회할 애니 ID
            @Param("currentUserId") Long currentUserId // 현재 사용자 ID(비로그인 null 허용)
    ); // 인터페이스 메서드 끝

    List<EpisodeDto> findEpisodesByAniId(@Param("aniId") Long aniId); // 상세: 에피소드 리스트 (anime 기준)
    List<GenreSimpleDto> findGenresByAniId(@Param("aniId") Long aniId); // 상세: 장르 리스트 (anime 기준)
    List<StudioSimpleDto> findStudiosByAniId(@Param("aniId") Long aniId); // 상세: 제작사 리스트 (anime 기준)
}


