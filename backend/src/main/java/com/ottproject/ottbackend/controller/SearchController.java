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

import java.util.List;

/**
 * 검색 컨트롤러
 * - 자동완성 GET /api/search/suggest?q=
 * - 통합 검색 GET /api/search?query=&genreIds=&tagIds=&sort=&page=&size=
 */
@RestController // REST 컨트롤러 선언
@RequiredArgsConstructor // final 필드에 대한 생성자 주입
@RequestMapping("/api/search") // 공통 URL prefix
public class SearchController {
    private final SearchService searchService; // 검색 서비스 의존성

    @GetMapping("/suggest") // 자동완성 엔드포인트
    public List<SearchSuggestTitleDto> suggest( // 제목만 응답
            @RequestParam("q") String q, // 키워드(최소 1자)
            @RequestParam(value = "limit", defaultValue = "10") int limit // 최대 건수(기본 10)
    ) {
        return searchService.suggest(q, limit); // 서비스 호출
    }

    @GetMapping // 통합 검색 엔드포인트
    public PagedResponse<AnimeListDto> search( // 페이지 응답
            @RequestParam(required = false) String query, // 키워드(부분일치)
            @RequestParam(required = false, name = "genreIds") List<Long> genreIds, // 장르 AND
            @RequestParam(required = false, name = "tagIds") List<Long> tagIds, // 태그 OR
            @RequestParam(defaultValue = "id") String sort, // 정렬 키
            @RequestParam(defaultValue = "0") int page, // 페이지 번호
            @RequestParam(defaultValue = "20") int size // 페이지 크기
    ) {
        return searchService.search(query, genreIds, tagIds, sort, page, size); // 서비스 호출
    }
}
