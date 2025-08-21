package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.AnimeListDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.dto.SearchSuggestTitleDto;
import com.ottproject.ottbackend.mybatis.SearchQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SearchService
 *
 * 큰 흐름
 * - 자동완성/본검색을 위한 읽기 전용 서비스를 제공한다(MyBatis 연동).
 *
 * 메서드 개요
 * - suggest: 자동완성(제목만)
 * - search: 본검색(키워드 + 장르 AND + 태그 OR + 정렬 + 페이지)
 */
@Slf4j
@Service // 스프링 서비스 컴포넌트
@RequiredArgsConstructor // final 필드에 생성자 주입
@Transactional(readOnly = true) // 읽기 전용 트랜잭션
public class SearchService {
    private final SearchQueryMapper mapper; // Mybatis 매퍼 의존성

    public List<SearchSuggestTitleDto> suggest(String q, int limit) { // 자동완성 메서드
        String query = (q == null) ? "" : q.trim(); // 공백 트림
        if (query.isEmpty()) return List.of(); // 최소 1자 보장
        int safeLimit = (limit <= 0 || limit > 50) ? 10 : limit; // 기본 10, 상한 50
        return mapper.suggestTitles(query, safeLimit); // 매퍼 호출
    }

    public PagedResponse<AnimeListDto> search(String query, List<Long> genreIds, List<Long> tagIds, String sort, int page, int size) { // 본검색 메서드
        String q = (query == null) ? "" : query.trim(); // 트림
        List<Long> distinctGenres = (genreIds == null) ? null : genreIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList()); // 장르 정제
        Integer genreCount = (distinctGenres == null) ? 0 : distinctGenres.size(); // AND 개수
        List<Long> distinctTags = (tagIds == null) ? null : tagIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList()); // 태그 정제
        int limit = size; // LIMIT 계산
        int offset = Math.max(page, 0) * size; // OFFSET 계산

        List<AnimeListDto> items = mapper.searchAnimes(q, distinctGenres, genreCount, distinctTags, sort, limit, offset); // 목록 조회
        long total = mapper.countSearchAnimes(q, distinctGenres, genreCount, distinctTags); // 총 개수 조회
        return new PagedResponse<>(items, total, page, size); // 페이지 응답 생성
    }
}
