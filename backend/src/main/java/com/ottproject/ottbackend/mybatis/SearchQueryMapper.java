package com.ottproject.ottbackend.mybatis;

import com.ottproject.ottbackend.dto.AnimeListDto;
import com.ottproject.ottbackend.dto.SearchSuggestTitleDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 검색 전용 MyBatis 매퍼
 * 자동완성(제목만)
 * 통합 검색(키워드 + 장르 AND + 태그 OR + 정렬 + 페이지)
 */
@Mapper
public interface SearchQueryMapper {
    List<SearchSuggestTitleDto> suggestTitles( // 자동완성: 제목만 반환
            @Param("q") String q, // 키워드(부분일치, ILIKE)
            @Param("limit") int limit // 최대 건수(기본 10)
    );

    List<AnimeListDto> searchAnimes( // 본검색: 목록
            @Param("query") String query, // 키워드(부분일치)
            @Param("genreIds") List<Long> genreIds, // 장르 AND 대상 IDs
            @Param("genreCount") Integer genreCount, // AND 매칭 개수
            @Param("tagIds") List<Long> tagIds, // 태그 OR 대상 IDs
            @Param("sort") String sort, // 정렬 키
            @Param("limit") int limit, // 페이지 크기
            @Param("offset") int offset // 오프셋
    );

    long countSearchAnimes( // 본검색: 총 개수
            @Param("query") String query, // 키워드
            @Param("genreIds") List<Long> genreIds, // 장르 Ids
            @Param("genreCount") Integer genreCount, // AND 개수
            @Param("tagIds") List<Long> tagIds // 태그 IDs
    );
}
