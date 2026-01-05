package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.AnimeDetailDto;
import com.ottproject.ottbackend.dto.AnimeListDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.service.AnimeQueryService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import com.ottproject.ottbackend.service.PersonalizedRecommendationService;
import com.ottproject.ottbackend.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.util.List;

/**
 * AnimeController
 *
 * 큰 흐름
 * - 애니 목록/상세를 조회한다. 목록은 필터/정렬/페이지네이션을 지원한다.
 *
 * 엔드포인트 개요
 * - GET /api/anime: 목록 조회(필터/정렬/페이지)
 * - GET /api/anime/{aniId}: 상세 조회(로그인 시 isFavorited 포함)
 */
@RequiredArgsConstructor
@RestController 
@RequestMapping("/api/anime") 
public class AnimeController {

    private final AnimeQueryService queryService; 
    private final SecurityUtil securityUtil; 
    private final PersonalizedRecommendationService personalizedRecommendationService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 애니 목록 조회(페이지네이션)
     */
    @Operation(summary = "애니 목록 조회", description = "필터/정렬/페이지네이션을 적용해 애니 목록을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping // GET /api/anime -> 목록 조회
    public PagedResponse<AnimeListDto> list( 
            @RequestParam(required = false) AnimeStatus status,
            @RequestParam(required = false, name = "genreIds") List<Long> genreIds,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Integer year, // ?year=2024 방영 연도 필터(옵션)
            @RequestParam(required = false) Integer quarter, // ?quarter=3 분기 필터(옵션)
            @RequestParam(required = false) String type, // 출시 타입 필터(옵션)
            @RequestParam(required = false) Boolean isDub, // ?isDub=true 더빙 여부(옵션)
            @RequestParam(required = false) Boolean isSubtitle, // ?isSubtitle=true 자막 여부(옵션)
            @RequestParam(required = false) Boolean isExclusive, // ?isExclusive=true 독점 여부(옵션)
            @RequestParam(required = false) Boolean isCompleted, // ?isCompleted=true 완결 여부(옵션)
            @RequestParam(required = false) Boolean isNew, // ?isNew=true 신작 여부(옵션)
            @RequestParam(required = false) Boolean isPopular, //?isPopular=true 인기 여부(옵션)
            @RequestParam(defaultValue = "id") String sort, //?sort-rating|year|popular|id 정렬 키(기본 id)
            @RequestParam(defaultValue = "0") int page, // ?page=0 페이지 번호(0-base, 기본 0)
            @RequestParam(defaultValue = "20") int size, // ?size=20 페이지 크기 (기본 20)
            @RequestParam(required = false, name = "tagIds") List<Long> tagIds // NEW: 태그 OR 필터
    ) {
        // 위 필터/정렬/페이지 정보를 서비스에 위임하여 MyBatis 쿼리 실행 후 페이지 응답으로 반환
        return queryService.list(
            status, genreIds, minRating, year, quarter, type,
            isDub, isSubtitle, isExclusive, isCompleted, isNew, isPopular,
            sort, page, size, tagIds
        );
    }

    /**
     * 애니 상세 조회
     */
    @Operation(summary = "애니 상세 조회", description = "에피소드/장르/제작사 포함 단건 상세 정보를 반환합니다. 로그인 시 isFavorited 포함")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{aniId}") // GET /api/anime/{aniId} -> 상세 조회
    public AnimeDetailDto detail( // 단건 상세 DTO 반환
            @Parameter(description = "애니 ID", required = true) @PathVariable Long aniId, // 경로 변수{aniId}를 Long 타입으로 바인딩
            HttpSession session // 로그인 사용자 여부 확인
    ) {
        Long userId = securityUtil.getCurrentUserIdOrNull(session); // 로그인 시 사용자 ID, 아니면 null
        return queryService.detail(aniId, userId); // isFavorited 포함 상세 반환
    }

    /**
     * 추천 애니메이션 조회 (개인화)
     */
    @Operation(summary = "개인화 추천 애니메이션 조회", description = "사용자 찜/시청/평점 기반 개인화 추천 애니메이션 목록을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/recommended") // GET /api/anime/recommended -> 개인화 추천 애니메이션 조회
    public List<AnimeListDto> getRecommended(
            @RequestParam(defaultValue = "6") int size
    ) {
        Long userId = securityUtil.getCurrentUserIdOrNull(null);
        
        if (userId != null) {
            // 로그인 사용자: 개인화 추천
            return personalizedRecommendationService.getPersonalizedRecommendations(userId, size);
        } else {
            // 비로그인 사용자: 기본 추천 (인기작)
            return queryService.list(
                    null, // status
                    null, // genreIds
                    null, // minRating
                    null, // year
                    null, // quarter
                    null, // type
                    null, // isDub
                    null, // isSubtitle
                    null, // isExclusive
                    null, // isCompleted
                    null, // isNew
                    true, // isPopular
                    "rating", // sort
                    0, // page
                    size, // size
                    null // tagIds
            ).getItems();
        }
    }

    /**
     * 인기 애니메이션 조회
     */
    @Operation(summary = "인기 애니메이션 조회", description = "평점과 조회수를 기준으로 인기 애니메이션 목록을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/popular") // GET /api/anime/popular -> 인기 애니메이션 조회
    public List<AnimeListDto> getPopular() {
        // 평점 기준으로 정렬하여 상위 10개 반환
        return queryService.list(
                null, // status
                null, // genreIds
                null, // minRating
                null, // year
                null, // quarter
                null, // type
                null, // isDub
                null, // isSubtitle
                null, // isExclusive
                null, // isCompleted
                null, // isNew
                null, // isPopular
                "rating", // sort
                0, // page
                10, // size
                null // tagIds
        ).getItems();
    }

    /**
     * 실시간 트렌딩(24h) 상위 작품 조회
     */
    @Operation(summary = "실시간 트렌딩(24h)", description = "최근 24시간 누적 활동 기반 상위 작품을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/trending-24h")
    public List<AnimeListDto> getTrending24h(@RequestParam(defaultValue = "10") int limit) {
        ZSetOperations<String, Object> zset = redisTemplate.opsForZSet();
        var tuples = zset.reverseRangeWithScores("trend:24h", 0, Math.max(0, limit - 1));
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<Long> ids = tuples.stream()
                .filter(t -> t.getValue() != null)
                .map(t -> t.getValue().toString())
                .filter(s -> {
                    try { Long.parseLong(s); return true; } catch (Exception e) { return false; }
                })
                .map(Long::valueOf)
                .toList();
        // ID 목록으로 상세 카드 조회 (입력 순서 보존)
        return queryService.listByIds(ids);
    }

    /**
     * 요일별 신작 조회
     */
    @Operation(summary = "요일별 신작 조회", description = "broadcast_day 기준으로 요일별 애니 목록을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/weekly/{day}")
    public List<AnimeListDto> getWeeklyByDay(
            @Parameter(description = "요일 (monday..sunday)", required = true) @PathVariable String day,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return queryService.getWeeklyByDay(day, limit);
    }

    /**
     * 전체 장르/태그 마스터 목록 반환
     */
    @Operation(summary = "장르 목록", description = "활성 장르 전체를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/genres")
    public List<com.ottproject.ottbackend.dto.GenreSimpleDto> getGenres() {
        return queryService.getAllGenres();
    }

    @Operation(summary = "태그 목록", description = "전체 태그를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/tags")
    public List<com.ottproject.ottbackend.dto.TagSimpleDto> getTags() {
        return queryService.getAllTags();
    }

    @Operation(summary = "시즌 목록", description = "애니메이션에 존재하는 시즌 목록을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/seasons")
    public List<String> getSeasons() {
        return queryService.getAllSeasons();
    }

    @Operation(summary = "년도/분기 옵션", description = "년도별 및 분기별 필터링 옵션을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/year-options")
    public List<com.ottproject.ottbackend.dto.YearOptionDto> getYearOptions() {
        return queryService.getYearOptions();
    }

    @Operation(summary = "상태 목록", description = "애니메이션 방영 상태 목록을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/statuses")
    public List<com.ottproject.ottbackend.dto.StatusOptionDto> getStatuses() {
        return queryService.getAllStatuses();
    }

    @Operation(summary = "타입 목록", description = "애니메이션 출시 타입 목록을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/types")
    public List<com.ottproject.ottbackend.dto.TypeOptionDto> getTypes() {
        return queryService.getAllTypes();
    }

    /**
     * 사용자 활동 기록 (개인화 추천용)
     */
    @Operation(summary = "사용자 활동 기록", description = "시청/찜/평점 활동을 기록하여 개인화 추천에 활용합니다.")
    @ApiResponse(responseCode = "200", description = "기록 성공")
    @PostMapping("/activity")
    public void recordActivity(
            @RequestParam Long animeId,
            @RequestParam String activityType // view, favorite, rating
    ) {
        Long userId = securityUtil.getCurrentUserIdOrNull(null);
        if (userId != null) {
            personalizedRecommendationService.recordUserActivity(userId, animeId, activityType);
        }
    }
}
