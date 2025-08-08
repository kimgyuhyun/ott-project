package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.AniDetailDto;
import com.ottproject.ottbackend.dto.AniListDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.service.AniQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor // final 필드 기반 생성자 자동 생성(의존성 주입)
@RestController // JSON 기반 REST 컨트롤러로 등록
@RequestMapping("/api/anime") // 공통 URL prefix 설정
public class AniController { // 애니 목록/상세 조회 컨트롤러

    private final AniQueryService queryService; // 조회 서비스 의존성

    @GetMapping // GET /api/anime -> 목록 조회
    public PagedResponse<AniListDto> list( // 표준 페이지 포멧으로 목록 반환
            @RequestParam(required = false) AnimeStatus status, // 쿼리스트링 ?status=COMPLETED -> ENUM 으로 바인딩(옵션)
            @RequestParam(required = false) Long genreId, // ?genreId=1 장르 필터(옵션)
            @RequestParam(required = false) Double minRating, // ?minRating=4.0 최소 평점 필터(옵션)
            @RequestParam(required = false) Integer year, // ?year=2024 방영 연도 필터(옵션)
            @RequestParam(required = false) Boolean isDub, // ?isDub=true 더빙 여부(옵션)
            @RequestParam(required = false) Boolean isSubtitle, // ?isSubtitle=true 자막 여부(옵션)
            @RequestParam(required = false) Boolean isExclusive, // ?isExclusive=true 독점 여부(옵션)
            @RequestParam(required = false) Boolean isCompleted, // ?isCompleted=true 완결 여부(옵션)
            @RequestParam(required = false) Boolean isNew, // ?isNew=true 신작 여부(옵션)
            @RequestParam(required = false) Boolean isPopular, //?isPopular=true 인기 여부(옵션)
            @RequestParam(defaultValue = "id") String sort, //?sort-rating|year|popular|id 정렬 키(기본 id)
            @RequestParam(defaultValue = "0") int page, // ?page=0 페이지 번호(0-base, 기본 0)
            @RequestParam(defaultValue = "20") int size // ?size=20 페이지 크기 (기본 20)
            ) {
        // 위 필터/정렬/페이지 정보를 서비스에 위임하여 MyBatis 쿼리 실행 후 페이지 응답으로 반환
        return queryService.list(
                status, genreId, minRating, year,
                isDub, isSubtitle, isExclusive, isCompleted, isNew, isPopular,
                sort, page, size
        );
    }

    @GetMapping("/{aniId}") // GET /api/anime/{aniId} -> 상세 조회
    public AniDetailDto detail( // 단건 상세 DTO 반환
            @PathVariable Long aniId // 경로 변수{aniId}를 Long 타입으로 바인딩
    ) {
        return queryService.detail(aniId); // 헤더/더보기 + 에피소드/장르/제작사까지 채워서 반환
    }
}
