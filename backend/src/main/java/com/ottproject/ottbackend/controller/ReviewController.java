package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.dto.ReviewResponseDto;
import com.ottproject.ottbackend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor // final 필드 기반 생성자 자동 생성(의존성 주입)
@RestController // REST 컨트롤러로 등록(메서드 반환값을 JSON 으로 직렬화)
@RequestMapping("/api/anime/{aniId}/reviews") // 상세 페이지 하위: 리뷰 컬렉션 경로
public class ReviewController { // 리뷰 목록/작성/일괄삭제 담당 컨트롤러

    private final ReviewService reviewService; // 비즈니스 로직을 담당하는 서비스 주입

    @GetMapping // HTTP GET /api/anime/{aniId}/reviews
    public ResponseEntity<PagedResponse<ReviewResponseDto>> list( // 리뷰 목록(페이지네이션) 반환
            @PathVariable Long aniId, // 경로변수: 애니 ID
            @RequestParam(required = false) Long currentUserId, // 현재 사용자 ID(좋아요 여부 계산용, 선택)
            @RequestParam(defaultValue = "latest") String sort, // 정렬 기준(기본: 최신순)
            @RequestParam(defaultValue = "0") int page, // 페이지 번호(0-base)
            @RequestParam(defaultValue = "10") int size // 페이지 크기
    ) {
        // 서비스 호출: 애니 ID 기준 리뷰 목록 + 총 개수 조회 후 페이지 응답으로 래핑
        return ResponseEntity.ok(reviewService.list(aniId, currentUserId, sort, page, size) // 200 OK + 본문
        );
    }

    @PostMapping // HTTP POST /api/anime/{aniId}/reviews
    public ResponseEntity<Long> create( // 생성된 리뷰의 PK(ID)를 반환
            @PathVariable Long aniId, // 경로 변수: 애니 ID
            @RequestParam Long userId, // 작성자 ID(인증 연동 전 임시)
            @RequestParam(required = false) String content, // 내용(선택)
            @RequestParam(required = false) Double rating // 평점(선택)
    ) {
        Long id = reviewService.create(userId, aniId, content, rating); // 리뷰 생성
        return ResponseEntity.ok(id); // 200 OK + 리뷰 ID
    }

    @DeleteMapping // DELETE /api/anime/{aniId]/reviews
    public ResponseEntity<Void> deleteAllByAni( // 특정 애니의 리뷰 일괄 삭제(관리용)
            @PathVariable Long aniId // 경로변수: 애니 ID
    ) {
        reviewService.deleteHardByAniList(aniId); // 일괄 하드 삭제
        return ResponseEntity.noContent().build(); // 204 No Content
    }
}
