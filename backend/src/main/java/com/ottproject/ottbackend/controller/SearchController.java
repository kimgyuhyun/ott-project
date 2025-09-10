package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.AnimeListDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.dto.SearchSuggestTitleDto;
import com.ottproject.ottbackend.service.SearchService;
import com.ottproject.ottbackend.service.RecentSearchService;
import com.ottproject.ottbackend.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import java.util.Base64;
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
    private final RecentSearchService recentSearchService; // 최근 검색어 서비스 의존성
    private final SecurityUtil securityUtil; // 보안 유틸 의존성

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

    @Operation(summary = "최근 검색어 조회", description = "사용자의 최근 검색어 목록을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/recent")
    public ResponseEntity<List<String>> getRecentSearches(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String subjectId = getSubjectId(request, response);
        List<String> searches = recentSearchService.list(subjectId);
        return ResponseEntity.ok(searches);
    }

    @Operation(summary = "최근 검색어 추가", description = "검색어를 최근 검색어 목록에 추가합니다.")
    @ApiResponse(responseCode = "200", description = "추가 성공")
    @PostMapping("/recent")
    public ResponseEntity<List<String>> addRecentSearch(
            @RequestBody AddRecentSearchRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String subjectId = getSubjectId(httpRequest, httpResponse);
        List<String> searches = recentSearchService.add(subjectId, request.getTerm());
        return ResponseEntity.ok(searches);
    }

    @Operation(summary = "최근 검색어 삭제", description = "특정 검색어를 삭제하거나 전체 검색어를 삭제합니다.")
    @ApiResponse(responseCode = "200", description = "삭제 성공")
    @DeleteMapping("/recent")
    public ResponseEntity<?> deleteRecentSearch(
            @RequestParam(required = false) String term,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String subjectId = getSubjectId(request, response);
        
        if (term != null && !term.trim().isEmpty()) {
            // 특정 검색어 삭제
            List<String> searches = recentSearchService.remove(subjectId, term);
            return ResponseEntity.ok(searches);
        } else {
            // 전체 삭제
            recentSearchService.clear(subjectId);
            return ResponseEntity.noContent().build();
        }
    }

    /**
     * 주체 ID 생성 (로그인 사용자 또는 익명 사용자)
     */
    private String getSubjectId(HttpServletRequest request, HttpServletResponse response) {
        // 로그인 사용자 확인
        HttpSession session = request.getSession(false);
        Long userId = securityUtil.getCurrentUserIdOrNull(session);
        if (userId != null) {
            return "user:" + userId;
        }
        
        // 익명 사용자 - anonId 쿠키 확인/발급
        String anonId = getAnonId(request, response);
        return "anon:" + anonId;
    }

    /**
     * 익명 사용자 ID 쿠키 처리
     */
    private String getAnonId(HttpServletRequest request, HttpServletResponse response) {
        // 기존 쿠키 확인
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("anonId".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        
        // 새 anonId 생성 및 쿠키 설정
        String anonId = generateAnonId();
        Cookie cookie = new Cookie("anonId", anonId);
        cookie.setPath("/");
        cookie.setMaxAge(365 * 24 * 60 * 60); // 365일
        cookie.setHttpOnly(false); // 프론트에서 접근 가능
        response.addCookie(cookie);
        
        return anonId;
    }

    /**
     * 익명 사용자 ID 생성 (Base62 26자)
     */
    private String generateAnonId() {
        UUID uuid = UUID.randomUUID();
        byte[] bytes = new byte[16];
        long mostSigBits = uuid.getMostSignificantBits();
        long leastSigBits = uuid.getLeastSignificantBits();
        
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (mostSigBits >>> 8 * (7 - i));
            bytes[i + 8] = (byte) (leastSigBits >>> 8 * (7 - i));
        }
        
        String base64 = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return base64.substring(0, 26); // 26자로 제한
    }

    /**
     * 최근 검색어 추가 요청 DTO
     */
    public static class AddRecentSearchRequest {
        private String term;

        public String getTerm() {
            return term;
        }

        public void setTerm(String term) {
            this.term = term;
        }
    }
}
