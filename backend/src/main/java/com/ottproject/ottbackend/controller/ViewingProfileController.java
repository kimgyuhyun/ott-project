package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.ViewingProfileNameRequestDto;
import com.ottproject.ottbackend.dto.ViewingProfileResponseDto;
import com.ottproject.ottbackend.service.ViewingProfileService;
import com.ottproject.ottbackend.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ViewingProfileController
 *
 * 큰 흐름
 * - 계정에 딸린 시청 프로필의 목록·생성·이름변경·삭제와 "사용할 프로필 선택"을 제공한다.
 * - 선택 결과는 세션에 담는다. 프로필은 로그인 이후의 화면 상태라 계정 세션과 수명이 같다.
 *
 * 엔드포인트 개요
 * - GET    /api/profiles            : 내 프로필 목록(없으면 계정 이름으로 하나 만들어 반환)
 * - POST   /api/profiles            : 프로필 생성
 * - PATCH  /api/profiles/{id}       : 프로필 이름 변경
 * - DELETE /api/profiles/{id}       : 프로필 삭제
 * - POST   /api/profiles/{id}/select: 사용할 프로필 선택(세션에 보관)
 *
 * 아직 하지 않는 것
 * - 시청기록·찜·별점은 여전히 계정(user_id) 단위다. 선택한 프로필은 화면 상태일 뿐이고
 *   보이는 데이터를 가르지 않는다. 데이터 분리는 별도 작업이다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/profiles")
public class ViewingProfileController {

    /** 선택된 프로필을 담는 세션 키. */
    public static final String SELECTED_PROFILE_SESSION_KEY = "viewingProfileId";

    private final ViewingProfileService viewingProfileService;
    private final SecurityUtil securityUtil;

    @Operation(summary = "내 프로필 목록", description = "현재 계정의 시청 프로필을 만든 순서대로 반환합니다. 하나도 없으면 계정 이름으로 기본 프로필을 만들어 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<List<ViewingProfileResponseDto>> list(HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        return ResponseEntity.ok(viewingProfileService.listProfiles(userId));
    }

    @Operation(summary = "프로필 생성", description = "현재 계정에 시청 프로필을 추가합니다.")
    @ApiResponse(responseCode = "200", description = "생성 성공")
    @PostMapping
    public ResponseEntity<ViewingProfileResponseDto> create(
            @Valid @RequestBody ViewingProfileNameRequestDto request, HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        return ResponseEntity.ok(viewingProfileService.create(userId, request.getName()));
    }

    @Operation(summary = "프로필 이름 변경", description = "현재 계정의 시청 프로필 이름을 바꿉니다.")
    @ApiResponse(responseCode = "200", description = "변경 성공")
    @PatchMapping("/{profileId}")
    public ResponseEntity<ViewingProfileResponseDto> rename(
            @PathVariable Long profileId,
            @Valid @RequestBody ViewingProfileNameRequestDto request,
            HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        return ResponseEntity.ok(viewingProfileService.rename(userId, profileId, request.getName()));
    }

    @Operation(summary = "프로필 삭제", description = "현재 계정의 시청 프로필을 삭제합니다. 마지막 하나는 삭제할 수 없습니다.")
    @ApiResponse(responseCode = "204", description = "삭제 완료")
    @DeleteMapping("/{profileId}")
    public ResponseEntity<Void> delete(@PathVariable Long profileId, HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        viewingProfileService.delete(userId, profileId);
        clearSelectionIfSame(session, profileId); // 지운 프로필이 선택돼 있었다면 선택도 지운다
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "프로필 선택", description = "사용할 시청 프로필을 고릅니다. 선택은 세션에 보관됩니다.")
    @ApiResponse(responseCode = "200", description = "선택 성공")
    @PostMapping("/{profileId}/select")
    public ResponseEntity<ViewingProfileResponseDto> select(@PathVariable Long profileId, HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        ViewingProfileResponseDto profile = viewingProfileService.requireOwnedProfile(userId, profileId);
        session.setAttribute(SELECTED_PROFILE_SESSION_KEY, profile.getId());
        return ResponseEntity.ok(profile);
    }

    private void clearSelectionIfSame(HttpSession session, Long deletedProfileId) {
        Object selected = session.getAttribute(SELECTED_PROFILE_SESSION_KEY);
        if (deletedProfileId.equals(selected)) {
            session.removeAttribute(SELECTED_PROFILE_SESSION_KEY);
        }
    }
}
