package com.ottproject.ottbackend.controller; // 컨트롤러 패키지

import com.ottproject.ottbackend.dto.AnimeDetailDto;
import com.ottproject.ottbackend.dto.FavoriteAnimeDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.service.AnimeQueryService;
import com.ottproject.ottbackend.service.FavoriteAnimeService;
import com.ottproject.ottbackend.util.SecurityUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * 찜 컨트롤러
 * - 작품 찜 토글, 마이페이지 찜 목록, 상세+찜 여부 조회
 */
@RestController // REST 컨트롤러
@RequiredArgsConstructor // 생성자 주입
public class FavoriteAnimeController { // 찜 전용 컨트롤러
    private final FavoriteAnimeService favoriteAnimeService; // 찜 서비스
    private final AnimeQueryService animeQueryService; // 상세 조회(찜 여부 포함)
    private final SecurityUtil securityUtil; // 세션 → userId 해석 유틸

    @Operation(summary = "찜 토글", description = "작품 찜 상태를 토글합니다.")
    @ApiResponse(responseCode = "200", description = "토글 결과 반환")
    @PostMapping("/api/anime/{aniId}/favorite") // 찜 토글 엔드포인트
    public ResponseEntity<Boolean> toggle( // true:on, false:off 응답
                                           @Parameter(description = "애니 ID") @PathVariable Long aniId, // 애니 ID
                                           HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = securityUtil.requireCurrentUserId(session); // 현재 사용자 ID 확인(401 처리)
        boolean state = favoriteAnimeService.toggle(aniId, userId); // 토글 실행
        return ResponseEntity.ok(state); // 200 OK 반환
    }

    @Operation(summary = "찜 목록", description = "마이페이지의 찜 목록을 페이지네이션으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/api/mypage/favorites/anime") // 마이페이지 내 찜 목록
    public ResponseEntity<PagedResponse<FavoriteAnimeDto>> list( // 페이지 응답
                                                                 @RequestParam(defaultValue = "0") int page, // 페이지 번호
                                                                 @RequestParam(defaultValue = "20") int size, // 페이지 크기
                                                                 @RequestParam(defaultValue = "favoritedAt") String sort, // 정렬키
                                                                 HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = securityUtil.requireCurrentUserId(session); // 현재 사용자 ID 확인
        PagedResponse<FavoriteAnimeDto> body = favoriteAnimeService.list(userId, page, size, sort); // 목록 조회
        return ResponseEntity.ok(body); // 200 OK
    }

    @Operation(summary = "상세+찜 여부", description = "작품 상세 정보와 현재 사용자 찜 여부를 함께 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/api/anime/{aniId}/detail") // 상세(찜 여부 포함) 예시
    public ResponseEntity<AnimeDetailDto> detailWithFavorite( // 상세 + isFavorited
                                                              @Parameter(description = "애니 ID") @PathVariable Long aniId, // 애니 ID
                                                              HttpSession session // 세션에서 사용자 확인(옵션)
    ) {
        Long userId = securityUtil.getCurrentUserIdOrNull(session); // 로그인 시 ID, 아니면 null
        AnimeDetailDto dto = animeQueryService.detail(aniId, userId); // MyBatis 확장 호출
        if (dto == null) return ResponseEntity.notFound().build(); // 404 처리
        return ResponseEntity.ok(dto); // 200 OK
    }
}