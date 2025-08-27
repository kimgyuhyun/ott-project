package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.AnimeListDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.dto.SearchSuggestTitleDto;
import com.ottproject.ottbackend.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * SearchController
 *
 * 큰 흐름
 * - 자동완성과 통합 검색을 제공한다.
 *
 * 엔드포인트 개요
 * - GET /api/search/suggest: 자동완성
 * - GET /api/search: 통합 검색(키워드/장르AND/태그OR/정렬/페이지)
 */
@Tag(name = "검색", description = "자동완성 및 통합 검색 API")
@RestController // REST 컨트롤러 선언
@RequiredArgsConstructor // final 필드에 대한 생성자 주입
@RequestMapping("/api/search") // 공통 URL prefix
public class SearchController {
    private final SearchService searchService; // 검색 서비스 의존성

    @Operation(summary = "자동완성 검색", description = "제목 기반 자동완성 검색 결과를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "검색 성공")
    @GetMapping("/suggest") // 자동완성 엔드포인트
    public List<SearchSuggestTitleDto> suggest( // 제목만 응답
            @Parameter(description = "검색 키워드 (q 또는 query 파라미터 사용)", required = false) 
            @RequestParam(value = "q", required = false) String q, // 키워드 별칭 1
            @Parameter(description = "검색 키워드 (q 또는 query 파라미터 사용)", required = false) 
            @RequestParam(value = "query", required = false) String query, // 키워드 별칭 2
            @Parameter(description = "최대 반환 건수", required = false) 
            @RequestParam(defaultValue = "10") int limit // 최대 건수(기본 10)
    ) {
        String keyword = (q != null && !q.isBlank()) ? q : query; // q 우선, 없으면 query 사용
        return searchService.suggest(keyword, limit); // 서비스 호출
    }

    @Operation(summary = "통합 검색", description = "키워드, 장르, 태그, 정렬, 페이지네이션을 지원하는 통합 검색을 제공합니다.")
    @ApiResponse(responseCode = "200", description = "검색 성공")
    @GetMapping // 통합 검색 엔드포인트
    public PagedResponse<AnimeListDto> search( // 페이지 응답
            @Parameter(description = "검색 키워드 (제목 부분일치)", required = false) 
            @RequestParam(required = false) String query, // 키워드(부분일치)
            @Parameter(description = "장르 ID 목록 (AND 조건, 모든 장르를 포함하는 작품만)", required = false) 
            @RequestParam(required = false, name = "genreIds") List<Long> genreIds, // 장르 AND
            @Parameter(description = "태그 ID 목록 (OR 조건, 하나라도 포함하는 작품)", required = false) 
            @RequestParam(required = false, name = "tagIds") List<Long> tagIds, // 태그 OR
            @Parameter(description = "정렬 기준 (id, rating, year, popular)", required = false) 
            @RequestParam(defaultValue = "id") String sort, // 정렬 키
            @Parameter(description = "페이지 번호 (0부터 시작)", required = false) 
            @RequestParam(defaultValue = "0") int page, // 페이지 번호
            @Parameter(description = "페이지 크기", required = false) 
            @RequestParam(defaultValue = "20") int size // 페이지 크기
    ) {
        return searchService.search(query, genreIds, tagIds, sort, page, size); // 서비스 호출
    }
}
