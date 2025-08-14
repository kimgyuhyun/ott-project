package com.ottproject.ottbackend.controller; // 컨트롤러 패키지

import com.ottproject.ottbackend.dto.AniDetailDto;
import com.ottproject.ottbackend.dto.FavoriteAniDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.service.AniQueryService;
import com.ottproject.ottbackend.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController // REST 컨트롤러
@RequiredArgsConstructor // 생성자 주입
public class FavoriteController { // 찜 전용 컨트롤러
    private final FavoriteService favoriteService; // 찜 서비스
    private final AniQueryService aniQueryService; // 상세 조회(찜 여부 포함)

    @PostMapping("/api/anime/{aniId}/favorite") // 찜 토글 엔드포인트
    public ResponseEntity<Boolean> toggle( // true:on, false:off 응답
                                           @PathVariable Long aniId, // 애니 ID
                                           @RequestParam Long userId // 사용자 ID(임시: 인증 대체)
    ) {
        boolean state = favoriteService.toggle(aniId, userId); // 토글 실행
        return ResponseEntity.ok(state); // 200 OK 반환
    }

    @GetMapping("/api/mypage/favorites/anime") // 마이페이지 내 찜 목록
    public ResponseEntity<PagedResponse<FavoriteAniDto>> list( // 페이지 응답
                                                               @RequestParam Long userId, // 사용자 ID
                                                               @RequestParam(defaultValue = "0") int page, // 페이지 번호
                                                               @RequestParam(defaultValue = "20") int size, // 페이지 크기
                                                               @RequestParam(defaultValue = "favoritedAt") String sort // 정렬키
    ) {
        PagedResponse<FavoriteAniDto> body = favoriteService.list(userId, page, size, sort); // 목록 조회
        return ResponseEntity.ok(body); // 200 OK
    }

    @GetMapping("/api/anime/{aniId}/detail") // 상세(찜 여부 포함) 예시
    public ResponseEntity<AniDetailDto> detailWithFavorite( // 상세 + isFavorited
                                                            @PathVariable Long aniId, // 애니 ID
                                                            @RequestParam(required = false) Long userId // 사용자 ID(옵션)
    ) {
        AniDetailDto dto = aniQueryService.detail(aniId, userId); // MyBatis 확장 호출
        if (dto == null) return ResponseEntity.notFound().build(); // 404 처리
        return ResponseEntity.ok(dto); // 200 OK
    }
}