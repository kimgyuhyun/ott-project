package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.ProrationPaymentRequestDto;
import com.ottproject.ottbackend.service.ProrationPaymentService;
import com.ottproject.ottbackend.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ProrationPaymentController
 *
 * 큰 흐름
 * - 차액 결제 전용 컨트롤러로 일반 결제와 분리된 엔드포인트를 제공한다.
 * - 플랜 업그레이드 시 차액 결제 세션 생성 및 완료 처리를 담당한다.
 *
 * 엔드포인트 개요
 * - POST /api/payments/proration: 차액 결제 세션 생성
 * - POST /api/payments/proration/{paymentId}/complete: 차액 결제 완료 처리
 */
@RestController
@RequiredArgsConstructor
@RequestMapping
public class ProrationPaymentController {
    private final ProrationPaymentService prorationPaymentService;
    private final SecurityUtil securityUtil;

    @Operation(summary = "차액 결제 세션 생성", description = "플랜 업그레이드 시 차액 결제 세션을 생성합니다.")
    @ApiResponse(responseCode = "200", description = "세션 생성 성공")
    @ApiResponse(responseCode = "400", description = "잘못된 요청: 유효하지 않은 플랜 또는 차액 없음")
    @PostMapping("/api/payments/proration")
    public ResponseEntity<Map<String, Object>> createProrationCheckout(
            @RequestBody ProrationPaymentRequestDto request,
            HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        Map<String, Object> response = prorationPaymentService.createProrationCheckout(userId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "차액 결제 완료 처리", description = "차액 결제 성공 시 플랜을 즉시 변경합니다.")
    @ApiResponse(responseCode = "200", description = "결제 완료 처리 성공")
    @ApiResponse(responseCode = "400", description = "잘못된 요청: 이미 처리된 결제 또는 유효하지 않은 상태")
    @ApiResponse(responseCode = "404", description = "결제를 찾을 수 없음")
    @PostMapping("/api/payments/proration/{paymentId}/complete")
    public ResponseEntity<Map<String, Object>> completeProrationPayment(
            @PathVariable Long paymentId,
            HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        Map<String, Object> response = prorationPaymentService.completeProrationPayment(userId, paymentId);
        return ResponseEntity.ok(response);
    }
}
