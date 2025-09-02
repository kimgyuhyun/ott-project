package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.MembershipPlanDto;
import com.ottproject.ottbackend.dto.MembershipSubscribeRequestDto;
import com.ottproject.ottbackend.dto.MembershipCancelMembershipRequestDto;
import com.ottproject.ottbackend.dto.MembershipPlanChangeRequestDto;
import com.ottproject.ottbackend.dto.MembershipPlanChangeResponseDto;
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
 * MembershipController
 *
 * 큰 흐름
 * - 멤버십 플랜 조회, 내 구독 상태 조회, 구독 신청/해지를 제공한다.
 *
 * 엔드포인트 개요
 * - GET /api/memberships/plans: 플랜 목록
 * - GET /api/users/me/membership: 내 멤버십 상태
 * - POST /api/memberships/subscribe: 구독 신청
 * - POST /api/memberships/cancel: 구독 해지(말일, 멱등)
 * - PUT /api/memberships/change-plan: 플랜 변경(업그레이드/다운그레이드)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping
public class MembershipController {
    private final MembershipReadService readService; // 읽기 전용 서비스 주입
    private final MembershipCommandService commandService; // 쓰기(변경) 서비스 주입
    private final  SecurityUtil securityUtil; // 세션에서 현재 사용자 ID를 확인하는 유틸리티

    @Operation(summary = "플랜 목록", description = "플랜ID/이름/월가격/기간/동시접속/품질을 반환합니다.") // Swagger 문서화: 요약/설명 정의
    @ApiResponse(responseCode = "200", description = "조회 성공") // Swagger 문서화: 200 응답 명세
    @GetMapping("/api/memberships/plans") // 유지: 퍼블릭 엔드포인트 별도 베이스 없음
    public ResponseEntity<List<MembershipPlanDto>> listPlans() { // 플랜 목록을 조회해 200 OK와 함께 반환하는 메서드
        return ResponseEntity.ok(readService.listPlans()); // 서비스에서 목록을 조회해 ResponseEntity.ok(...)로 감싸서 반환
    }

    @Operation(summary = "내 멤버십", description = "현재 플랜/만료일/자동갱신 여부/상태를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/api/users/me/membership") // 유지: 사용자 기준 개별 베이스
    public ResponseEntity<UserMembershipDto> myMembership(HttpSession session) { //세션 기반으로 현재 사용자 멤버십을 조회하는 메서드
        Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 사용자 식별(미인증이면 401 예외 반환)
        return ResponseEntity.ok(readService.getMyMembership(userId)); // 읽기 서비스로 상태 조회 후 200 OK 반환
    }

    @Operation(summary = "구독 신청", description = "플랜 코드로 정기 구독을 신청합니다.")
    @ApiResponse(responseCode = "200", description = "신청 완료: 최신 멤버십 상태 반환")
    @PostMapping("/api/memberships/subscribe")
    public ResponseEntity<UserMembershipDto> subscribe(@RequestBody MembershipSubscribeRequestDto dto, HttpSession session) { // 요청 바디의 플랜 코드로 구독 신청 처리
        Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 사용자 ID를 확인(401 가능)
        commandService.subscribe(userId, dto); // 쓰기 서비스에 위임하여 구독 생성/연장 로직 수행
        return ResponseEntity.ok(readService.getMyMembership(userId)); // 변경 직후 최신 멤버십 상태 반환
    }

    @Operation(summary = "구독 해지", description = "말일 해지로 예약합니다. 중복 방지를 위해 idempotencyKey를 전달할 수 있습니다.")
    @ApiResponse(responseCode = "200", description = "해지 완료: 최신 멤버십 상태 반환")
    @PostMapping("/api/memberships/cancel")
    public ResponseEntity<UserMembershipDto> cancel(@RequestBody MembershipCancelMembershipRequestDto dto, HttpSession session) { // 말일 해지 예약(멱등키로 중복 방지)
        Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 현재 사용자 ID 확인(401 가능)
        commandService.cancel(userId, dto); // 쓰기 서비스로 말일 해지 예약 수행(멱등키가 있으면 중복 방지)
        return ResponseEntity.ok(readService.getMyMembership(userId)); // 변경 직후 최신 멤버십 상태 반환
    }

    @Operation(summary = "멤버십 정기결제 재시작", description = "해지 예약된 멤버십의 정기결제를 다시 시작합니다.")
    @ApiResponse(responseCode = "200", description = "재시작 완료: 최신 멤버십 상태 반환")
    @PostMapping("/api/memberships/resume")
    public ResponseEntity<UserMembershipDto> resume(HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        commandService.resume(userId);
        return ResponseEntity.ok(readService.getMyMembership(userId));
    }

    @Operation(summary = "플랜 변경", description = "멤버십 플랜을 변경합니다. 업그레이드는 즉시 적용+차액결제, 다운그레이드는 다음 결제일부터 적용됩니다.")
    @ApiResponse(responseCode = "200", description = "변경 완료: 변경 결과 정보 반환")
    @ApiResponse(responseCode = "400", description = "잘못된 요청: 유효하지 않은 플랜 코드 또는 현재와 같은 플랜")
    @PutMapping("/api/memberships/change-plan")
    public ResponseEntity<MembershipPlanChangeResponseDto> changePlan(
            @RequestBody MembershipPlanChangeRequestDto request, 
            HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 사용자 ID 확인(401 가능)
        MembershipPlanChangeResponseDto response = commandService.changePlan(userId, request); // 플랜 변경 처리
        return ResponseEntity.ok(response); // 변경 결과 반환
    }

    @Operation(summary = "플랜 변경 예약 취소", description = "다음 결제일 전환 예약을 취소합니다.")
    @ApiResponse(responseCode = "200", description = "취소 완료")
    @PostMapping("/api/memberships/change-plan/cancel")
    public ResponseEntity<UserMembershipDto> cancelScheduledPlanChange(HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        commandService.cancelScheduledPlanChange(userId);
        return ResponseEntity.ok(readService.getMyMembership(userId));
    }
}
