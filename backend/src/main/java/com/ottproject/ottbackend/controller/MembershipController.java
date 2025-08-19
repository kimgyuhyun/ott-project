package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.MembershipPlanDto;
import com.ottproject.ottbackend.dto.MembershipSubscribeRequestDto;
import com.ottproject.ottbackend.dto.MembershipCancelMembershipRequestDto;
import com.ottproject.ottbackend.dto.UserMembershipDto;
import com.ottproject.ottbackend.service.MembershipCommandService;
import com.ottproject.ottbackend.service.MembershipReadService;
import com.ottproject.ottbackend.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 멤버십 API 컨트롤러
 * - 플랜 목록 조회
 * - 내 멤버십 상태 조회
 * - 구독 신청(정기)
 * - 구독 해지(말일 해지, 멱등키 지원)
 */
@RestController
@RequiredArgsConstructor
public class MembershipController {
    private final MembershipReadService readService; // 읽기 전용 서비스 주입
    private final MembershipCommandService commandService; // 쓰기(변경) 서비스 주입
    private final  SecurityUtil securityUtil; // 세션에서 현재 사용자 ID를 확인하는 유틸리티

    @Operation(summary = "플랜 목록", description = "플랜ID/이름/월가격/기간/동시접속/품질을 반환합니다.") // Swagger 문서화: 요약/설명 정의
    @ApiResponse(responseCode = "200", description = "조회 성공") // Swagger 문서화: 200 응답 명세
    @GetMapping("/api/memberships/plans") // HTTP GET /api/memberships/plans 로 매핑
    public ResponseEntity<List<MembershipPlanDto>> listPlans() { // 플랜 목록을 조회해 200 OK와 함께 반환하는 메서드
        return ResponseEntity.ok(readService.listPlans()); // 서비스에서 목록을 조회해 ResponseEntity.ok(...)로 감싸서 반환
    }

    @Operation(summary = "내 멤버십", description = "현재 플랜/만료일/자동갱신 여부/상태를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/api/users/me/membership") // HTTP GET /api/users/me/membership 로 매핑
    public ResponseEntity<UserMembershipDto> myMembership(HttpSession session) { //세션 기반으로 현재 사용자 멤버십을 조회하는 메서드
        Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 사용자 식별(미인증이면 401 예외 반환)
        return ResponseEntity.ok(readService.getMyMembership(userId)); // 읽기 서비스로 상태 조회 후 200 OK 반환
    }

    @Operation(summary = "구독 신청", description = "플랜 코드로 정기 구독을 신청합니다.")
    @ApiResponse(responseCode = "200", description = "신청 완료: 최신 멤버십 상태 반환")
    @PostMapping("/api/memberships/subscribe") // HTTP POST /api/memberships/subscribe 로 매핑
    public ResponseEntity<UserMembershipDto> subscribe(@RequestBody MembershipSubscribeRequestDto dto, HttpSession session) { // 요청 바디의 플랜 코드로 구독 신청 처리
        Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 사용자 ID를 확인(401 가능)
        commandService.subscribe(userId, dto); // 쓰기 서비스에 위임하여 구독 생성/연장 로직 수행
        return ResponseEntity.ok(readService.getMyMembership(userId)); // 변경 직후 최신 멤버십 상태 반환
    }

    @Operation(summary = "구독 해지", description = "말일 해지로 예약합니다. 중복 방지를 위해 idempotencyKey를 전달할 수 있습니다.")
    @ApiResponse(responseCode = "200", description = "해지 완료: 최신 멤버십 상태 반환")
    @PostMapping("/api/memberships/cancel") // HTTP POST /api/memberships/cancel 로 매핑
    public ResponseEntity<UserMembershipDto> cancel(@RequestBody MembershipCancelMembershipRequestDto dto, HttpSession session) { // 말일 해지 예약(멱등키로 중복 방지)
        Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 현재 사용자 ID 확인(401 가능)
        commandService.cancel(userId, dto); // 쓰기 서비스로 말일 해지 예약 수행(멱등키가 있으면 중복 방지)
        return ResponseEntity.ok(readService.getMyMembership(userId)); // 변경 직후 최신 멤버십 상태 반환
    }
}
