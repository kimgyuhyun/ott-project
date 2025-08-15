package com.ottproject.ottbackend.controller; // 컨트롤러 패키지

import com.ottproject.ottbackend.dto.AniDetailDto;
import com.ottproject.ottbackend.dto.FavoriteAniDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.service.AniQueryService;
import com.ottproject.ottbackend.service.FavoriteService;
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
public class FavoriteController { // 찜 전용 컨트롤러
    private final FavoriteService favoriteService; // 찜 서비스
    private final AniQueryService aniQueryService; // 상세 조회(찜 여부 포함)
    private final com.ottproject.ottbackend.util.AuthUtil authUtil; // 세션 → userId 해석 유틸

    @Operation(summary = "찜 토글", description = "작품 찜 상태를 토글합니다.")
    @ApiResponse(responseCode = "200", description = "토글 결과 반환")
    @PostMapping("/api/anime/{aniId}/favorite") // 찜 토글 엔드포인트
    public ResponseEntity<Boolean> toggle( // true:on, false:off 응답
                                           @Parameter(description = "애니 ID") @PathVariable Long aniId, // 애니 ID
                                           HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = authUtil.requireCurrentUserId(session); // 현재 사용자 ID 확인(401 처리)
        boolean state = favoriteService.toggle(aniId, userId); // 토글 실행
        return ResponseEntity.ok(state); // 200 OK 반환
    }

    @Operation(summary = "찜 목록", description = "마이페이지의 찜 목록을 페이지네이션으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/api/mypage/favorites/anime") // 마이페이지 내 찜 목록
    public ResponseEntity<PagedResponse<FavoriteAniDto>> list( // 페이지 응답
                                                               @RequestParam(defaultValue = "0") int page, // 페이지 번호
                                                               @RequestParam(defaultValue = "20") int size, // 페이지 크기
                                                               @RequestParam(defaultValue = "favoritedAt") String sort, // 정렬키
                                                               HttpSession session // 세션에서 사용자 확인
    ) {
        Long userId = authUtil.requireCurrentUserId(session); // 현재 사용자 ID 확인
        PagedResponse<FavoriteAniDto> body = favoriteService.list(userId, page, size, sort); // 목록 조회
        return ResponseEntity.ok(body); // 200 OK
    }

    @Operation(summary = "상세+찜 여부", description = "작품 상세 정보와 현재 사용자 찜 여부를 함께 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/api/anime/{aniId}/detail") // 상세(찜 여부 포함) 예시
    public ResponseEntity<AniDetailDto> detailWithFavorite( // 상세 + isFavorited
                                                            @Parameter(description = "애니 ID") @PathVariable Long aniId, // 애니 ID
                                                            HttpSession session // 세션에서 사용자 확인(옵션)
    ) {
        Long userId = authUtil.getCurrentUserIdOrNull(session); // 로그인 시 ID, 아니면 null
        AniDetailDto dto = aniQueryService.detail(aniId, userId); // MyBatis 확장 호출
        if (dto == null) return ResponseEntity.notFound().build(); // 404 처리
        return ResponseEntity.ok(dto); // 200 OK
    }
}