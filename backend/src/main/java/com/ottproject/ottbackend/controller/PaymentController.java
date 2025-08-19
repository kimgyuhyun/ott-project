package com.ottproject.ottbackend.controller; // 컨트롤러 패키지 선언

import com.ottproject.ottbackend.dto.PaymentCheckoutCreateRequestDto;
import com.ottproject.ottbackend.dto.PaymentCheckoutCreateSuccessResponseDto;
import com.ottproject.ottbackend.dto.PaymentHistoryItemDto;
import com.ottproject.ottbackend.dto.PaymentWebhookEventDto;
import com.ottproject.ottbackend.service.PaymentCommandService;
import com.ottproject.ottbackend.service.PaymentReadService;
import com.ottproject.ottbackend.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 API 컨트롤러
 *
 * 제공 기능:
 * - 체크아웃 생성(결제창 URL 반환)
 * - 웹훅 수신(성공/실패/취소/환불 반영, 멱등)
 * - 결제 이력 조회(MyBatis)
 */
@RestController // REST 컨트롤러 선언
@RequiredArgsConstructor // 생성자 주입
public class PaymentController { // 결제 컨트롤러 시작
	private final PaymentCommandService paymentCommandService; // 쓰기 서비스 주입
	private final PaymentReadService paymentReadService; // 읽기 서비스 주입
	private final SecurityUtil securityUtil; // 인증 사용자 식별 유틸

	@Operation(summary = "체크아웃 생성", description = "플랜 코드로 결제창 세션을 생성하고 redirectUrl을 반환합니다.") // Swagger 요약/설명
	@ApiResponse(responseCode = "200", description = "생성 성공") // Swagger 응답 코드 문서화
	@PostMapping("/api/payments/checkout") // HTTP POST 매핑: 체크아웃 생성
	public ResponseEntity<PaymentCheckoutCreateSuccessResponseDto> checkout(@RequestBody PaymentCheckoutCreateRequestDto dto, HttpSession session) { // 요청 바디/세션 수신
		Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 사용자 ID 확인(미인증 시 401)
		PaymentCheckoutCreateSuccessResponseDto res = paymentCommandService.checkout(userId, dto); // 서비스 위임으로 체크아웃 처리
		return ResponseEntity.ok(res); // 200 OK + 응답 바디 반환
	}

	@Operation(summary = "웹훅 수신(경로변수)", description = "게이트웨이가 결제 결과를 통지합니다. paymentId를 경로로 전달합니다.") // Swagger 문서화
	@ApiResponse(responseCode = "200", description = "반영 완료") // 200 문서화
	@PostMapping("/api/payments/{paymentId}/webhook") // HTTP POST 매핑: 경로변수로 paymentId 수신
	public ResponseEntity<Void> webhookWithPath(@PathVariable Long paymentId, @RequestBody PaymentWebhookEventDto event) { // 경로변수/바디 수신
		paymentCommandService.applyWebhookEvent(paymentId, event); // 서비스 위임으로 상태 반영(멱등)
		return ResponseEntity.ok().build(); // 바디 없이 200 OK 반환
	}

	@Operation(summary = "웹훅 수신(쿼리파라미터)", description = "게이트웨이가 결제 결과를 통지합니다. paymentId를 쿼리로 전달합니다.") // Swagger 문서화
	@ApiResponse(responseCode = "200", description = "반영 완료") // 200 문서화
	@PostMapping("/api/payments/webhook") // HTTP POST 매핑: 쿼리파라미터로 paymentId 수신
	public ResponseEntity<Void> webhookWithQuery(@RequestParam("paymentId") Long paymentId, @RequestBody PaymentWebhookEventDto event) { // 파라미터/바디 수신
		paymentCommandService.applyWebhookEvent(paymentId, event); // 서비스 위임으로 상태 반영(멱등)
		return ResponseEntity.ok().build(); // 바디 없이 200 OK 반환
	}

	@Operation(summary = "결제 이력 조회", description = "사용자의 결제/환불 이력을 최신순으로 반환합니다.") // Swagger 문서화
	@ApiResponse(responseCode = "200", description = "조회 성공") // 200 문서화
	@GetMapping("/api/payments/history") // HTTP GET 매핑: 이력 조회
	public ResponseEntity<List<PaymentHistoryItemDto>> history(
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @RequestParam(value = "start", required = false) LocalDateTime start, // 시작시각(선택)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @RequestParam(value = "end", required = false) LocalDateTime end, // 종료시각(선택)
			HttpSession session // 세션 수신
	) {
		Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 사용자 ID 확인
		List<PaymentHistoryItemDto> list = paymentReadService.listHistory(userId, start, end); // 서비스 위임으로 목록 조회
		return ResponseEntity.ok(list); // 200 OK + 목록 반환
	}

	@Operation(summary = "환불 요청", description = "결제 후 24시간 이내이며 누적 시청 5분 미만일 때만 환불합니다.") // Swagger 문서화
	@ApiResponse(responseCode = "200", description = "환불 완료") // 200 문서화
	@PostMapping("/api/payments/{paymentId}/refund") // HTTP POST 매핑: 환불 요청
	public ResponseEntity<Void> refund(@PathVariable Long paymentId, HttpSession session) { // 환불 API
		Long userId = securityUtil.requireCurrentUserId(session); // 로그인 사용자 확인
		paymentCommandService.refundIfEligible(userId, paymentId); // 정책 검증 후 환불 실행
		return ResponseEntity.ok().build(); // 바디 없이 200 OK
	}
}


