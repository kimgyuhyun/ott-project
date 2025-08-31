package com.ottproject.ottbackend.controller; // 컨트롤러 패키지 선언

import com.ottproject.ottbackend.dto.PaymentCheckoutCreateRequestDto;
import com.ottproject.ottbackend.dto.PaymentCheckoutCreateSuccessResponseDto;
import com.ottproject.ottbackend.dto.PaymentHistoryItemDto;
import com.ottproject.ottbackend.dto.PaymentMethodRegisterRequestDto;
import com.ottproject.ottbackend.dto.PaymentMethodResponseDto;
import com.ottproject.ottbackend.dto.PaymentMethodUpdateRequestDto;
import com.ottproject.ottbackend.dto.PaymentWebhookEventDto;
import com.ottproject.ottbackend.dto.PaymentResultResponseDto;
import com.ottproject.ottbackend.service.PaymentCommandService;
import com.ottproject.ottbackend.service.PaymentReadService;
import com.ottproject.ottbackend.service.PaymentMethodService;
import com.ottproject.ottbackend.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * PaymentController
 *
 * 큰 흐름(Javadoc)
 * - 결제 체크아웃 생성, 웹훅 수신 처리, 결제 이력 조회를 제공한다.
 * - 결제수단 등록/목록/기본 지정/수정/삭제로 정기결제 시 기본→보조 폴백을 지원한다.
 * - 모든 API에서 세션 기반 사용자 식별을 수행한다.
 *
 * 엔드포인트 개요
 * - POST /api/payments/checkout: 체크아웃 생성
 * - POST /api/payments/webhook: 웹훅 수신 (통합)
 * - GET /api/payments/{paymentId}/status: 결제 상태 확인
 * - GET /api/payments/history: 결제/환불 이력 조회
 * - POST/GET/PUT/DELETE/PATCH /api/payment-methods: 결제수단 CRUD/기본 지정
 * - POST /api/payments/{paymentId}/refund: 환불 요청
 */
@RestController // REST 컨트롤러 선언
@RequiredArgsConstructor // 생성자 주입
@RequestMapping("/api")
@Slf4j
public class PaymentController { // 결제 컨트롤러 시작
	private final PaymentCommandService paymentCommandService; // 쓰기 서비스 주입
	private final PaymentReadService paymentReadService; // 읽기 서비스 주입
	private final SecurityUtil securityUtil; // 인증 사용자 식별 유틸
    private final PaymentMethodService paymentMethodService; // 결제수단 서비스

	@Operation(summary = "체크아웃 생성", description = "플랜 코드로 결제창 세션을 생성하고 redirectUrl을 반환합니다.") // Swagger 요약/설명
	@ApiResponse(responseCode = "200", description = "생성 성공") // Swagger 응답 코드 문서화
	@PostMapping("/payments/checkout") // HTTP POST 매핑: 체크아웃 생성
	public ResponseEntity<PaymentCheckoutCreateSuccessResponseDto> checkout(@RequestBody PaymentCheckoutCreateRequestDto dto, HttpSession session) { // 요청 바디/세션 수신
		Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 사용자 ID 확인(미인증 시 401)
		PaymentCheckoutCreateSuccessResponseDto res = paymentCommandService.checkout(userId, dto); // 서비스 위임으로 체크아웃 처리
		return ResponseEntity.ok(res); // 200 OK + 응답 바디 반환
	}

    @Operation(summary = "결제수단 등록", description = "정기결제를 위한 저장 결제수단을 등록합니다.")
    @ApiResponse(responseCode = "200", description = "Registered")
    @PostMapping("/payment-methods")
    public ResponseEntity<Void> registerPaymentMethod(@RequestBody PaymentMethodRegisterRequestDto dto, HttpSession session) { // 결제수단 등록 엔드포인트
        Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 사용자 ID 확인
        paymentMethodService.register(userId, dto); // 서비스에 등록 위임
        return ResponseEntity.ok().build(); // 200 OK 반환
    }

    @Operation(summary = "결제수단 목록", description = "사용자의 저장 결제수단을 기본 우선으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping("/payment-methods")
    public ResponseEntity<List<PaymentMethodResponseDto>> listPaymentMethods(HttpSession session) { // 결제수단 목록 엔드포인트
        Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 사용자 ID 확인
        return ResponseEntity.ok(paymentMethodService.list(userId)); // 삭제 제외 DTO 목록 반환
    }

    @Operation(summary = "결제수단 기본 지정", description = "특정 결제수단을 기본으로 지정합니다.")
    @ApiResponse(responseCode = "200", description = "OK")
    @PutMapping("/payment-methods/{id}/default")
    public ResponseEntity<Void> setDefaultPaymentMethod(@PathVariable("id") Long id, HttpSession session) { // 기본 지정 엔드포인트
        Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 사용자 ID 확인
        paymentMethodService.setDefault(userId, id); // 서비스에 기본 지정 위임
        return ResponseEntity.ok().build(); // 200 OK 반환
    }

    @Operation(summary = "결제수단 삭제", description = "결제수단을 소프트 삭제합니다.")
    @ApiResponse(responseCode = "200", description = "Deleted")
    @DeleteMapping("/payment-methods/{id}")
    public ResponseEntity<Void> deletePaymentMethod(@PathVariable("id") Long id, HttpSession session) { // 삭제 엔드포인트
        Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 사용자 ID 확인
        paymentMethodService.delete(userId, id); // 서비스에 소프트 삭제 위임
        return ResponseEntity.ok().build(); // 200 OK 반환
    }

    @Operation(summary = "결제수단 수정", description = "결제수단의 일부 정보를 수정합니다.")
    @ApiResponse(responseCode = "200", description = "Updated")
    @PatchMapping("/payment-methods/{id}")
    public ResponseEntity<Void> updatePaymentMethod(@PathVariable("id") Long id, // 수정 엔드포인트
                                                    @RequestBody PaymentMethodUpdateRequestDto patch, // 부분 수정 페이로드
                                                    HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 사용자 ID 확인
        paymentMethodService.updatePartial(userId, id, patch); // 서비스에 부분 수정 위임
        return ResponseEntity.ok().build(); // 200 OK 반환
    }

	@Operation(summary = "웹훅 수신", description = "아임포트로부터 결제 결과 웹훅을 수신하여 처리합니다.") // Swagger 문서화
	@ApiResponse(responseCode = "200", description = "반영 완료") // 200 문서화
	@PostMapping("/payments/webhook") // HTTP POST 매핑: 통합된 웹훅 엔드포인트
	public ResponseEntity<Void> webhook(@RequestBody String rawBody, @RequestHeader HttpHeaders headers) { // 원문/헤더 수신
		paymentCommandService.processWebhook(headers, rawBody); // 통합된 웹훅 처리 메서드 호출
		return ResponseEntity.ok().build(); // 200 OK 반환
	}

	@Operation(summary = "웹훅 테스트", description = "개발 환경에서만 사용 가능한 웹훅 테스트 엔드포인트")
	@ApiResponse(responseCode = "200", description = "테스트 완료")
	@PostMapping("/payments/webhook/test")
	public ResponseEntity<Void> webhookTest(@RequestBody String rawBody, @RequestHeader HttpHeaders headers) {
		log.info("웹훅 테스트 시작");
		try {
			paymentCommandService.processWebhook(headers, rawBody);
			log.info("웹훅 테스트 성공");
			return ResponseEntity.ok().build();
		} catch (Exception e) {
			log.error("웹훅 테스트 실패", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	@Operation(summary = "결제 이력 조회", description = "사용자의 결제/환불 이력을 최신순으로 반환합니다.") // Swagger 문서화
	@ApiResponse(responseCode = "200", description = "조회 성공") // 200 문서화
	@GetMapping("/payments/history") // HTTP GET 매핑: 이력 조회
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
	@PostMapping("/payments/{paymentId}/refund") // HTTP POST 매핑: 환불 요청
	public ResponseEntity<Void> refund(@PathVariable Long paymentId, HttpSession session) { // 환불 API
		Long userId = securityUtil.requireCurrentUserId(session); // 로그인 사용자 확인
		paymentCommandService.refundIfEligible(userId, paymentId); // 정책 검증 후 환불 실행
		return ResponseEntity.ok().build(); // 바디 없이 200 OK
	}
	
	@Operation(summary = "결제 상태 확인", description = "결제 ID로 결제 상태를 조회합니다.") // Swagger 문서화
	@ApiResponse(responseCode = "200", description = "조회 성공") // 200 문서화
	@GetMapping("/payments/{paymentId}/status") // HTTP GET 매핑: 결제 상태 조회
	public ResponseEntity<PaymentResultResponseDto> checkPaymentStatus(@PathVariable Long paymentId, HttpSession session) { // 결제 상태 확인 API
		Long userId = securityUtil.requireCurrentUserId(session); // 세션에서 사용자 ID 확인
		PaymentResultResponseDto result = paymentReadService.getPaymentStatus(paymentId, userId); // 서비스 위임으로 상태 조회
		return ResponseEntity.ok(result); // 200 OK + 상태 정보 반환
	}
}


