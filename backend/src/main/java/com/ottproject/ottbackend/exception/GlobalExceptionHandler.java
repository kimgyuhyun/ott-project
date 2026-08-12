package com.ottproject.ottbackend.exception;

import com.ottproject.ottbackend.entity.ViewingProfile;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * GlobalExceptionHandler
 *
 * 큰 흐름
 * - 컨트롤러 전역의 예외를 표준 오류 바디(ApiError)로 변환하여 응답한다.
 *
 * 메서드 개요
 * - handleRse: ResponseStatusException → 상태/메시지 반영
 * - handleValidation: 검증 실패 → 첫 필드 에러 메시지 반영(400)
 * - handleDuplicateWebhookEvent: 웹훅 멱등키 경합 → 200(재전송 루프 차단)
 * - handleAny: 기타 예외 → 500/Internal error 고정 응답
 *
 * 응답 바디에 원본 예외 메시지나 클래스명을 싣지 않는다. 진단에 필요한 정보는 로그에만 남기고,
 * 클라이언트는 응답 헤더의 X-Request-Id 로 그 로그를 지목한다(MdcLoggingFilter 가 심는다).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiError> handleRse(ResponseStatusException ex) {
		log.warn("ResponseStatusException: status={}, reason={}", ex.getStatusCode(), ex.getReason(), ex);
		HttpStatus status = ex.getStatusCode() instanceof HttpStatus ? (HttpStatus) ex.getStatusCode() : HttpStatus.BAD_REQUEST; // 상태 추출
		return ResponseEntity.status(status).body(ApiError.builder().code("ERROR") .message(ex.getReason()).build()); // 코드/메시지 응답
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
		log.warn("Validation error", ex);
		String msg = ex.getBindingResult().getFieldErrors().stream()
				.findFirst().map(err -> err.getField() + ": " + err.getDefaultMessage()).orElse("Validation error"); // 첫 에러 메시지
		return ResponseEntity.badRequest().body(ApiError.builder().code("VALIDATION_ERROR").message(msg).build()); // 400 + 메시지
	}

	/**
	 * 멱등키 선삽입이 유니크 제약에 걸렸다 = 같은 이벤트를 다른 요청이 이미 처리 중/완료했다.
	 * 이쪽 트랜잭션은 롤백되지만 잃을 게 없으므로 200 으로 응답한다.
	 * 500 을 주면 PG 가 실패로 알고 재전송을 반복한다.
	 *
	 * 흡수 대상을 이 예외로 좁힌 이유: 웹훅 처리 중에는 멤버십/구독 행 생성 등에서도 제약 위반이 날 수 있는데,
	 * 그것까지 200 으로 삼키면 PG 가 성공으로 알고 재전송하지 않아 조용한 유실이 된다.
	 * 멱등키 경합이 아닌 제약 위반은 아래 handleAny 로 떨어져 500 이 되고, 재전송으로 복구된다.
	 */
	@ExceptionHandler(DuplicateWebhookEventException.class)
	public ResponseEntity<Void> handleDuplicateWebhookEvent(DuplicateWebhookEventException ex) {
		log.info("웹훅 중복 수신(멱등키 경합) - 200 처리: {}", ex.getMessage());
		return ResponseEntity.ok().build(); // 바디 없이 200 — 원래 웹훅 성공 응답과 같은 모양
	}

	@ExceptionHandler(ViewingProfileNotFoundException.class)
	public ResponseEntity<ApiError> handleViewingProfileNotFound(ViewingProfileNotFoundException ex) {
		log.warn("시청 프로필 없음: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiError.builder().code("PROFILE_NOT_FOUND").message("프로필을 찾을 수 없습니다.").build());
	}

	@ExceptionHandler(ViewingProfileLimitExceededException.class)
	public ResponseEntity<ApiError> handleViewingProfileLimit(ViewingProfileLimitExceededException ex) {
		log.warn("시청 프로필 상한 초과: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiError.builder().code("PROFILE_LIMIT_EXCEEDED")
						.message("프로필은 계정당 " + ViewingProfile.MAX_PER_ACCOUNT + "개까지 만들 수 있습니다.").build());
	}

	@ExceptionHandler(LastViewingProfileException.class)
	public ResponseEntity<ApiError> handleLastViewingProfile(LastViewingProfileException ex) {
		log.warn("마지막 시청 프로필 삭제 시도: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiError.builder().code("LAST_PROFILE").message("마지막 프로필은 삭제할 수 없습니다.").build());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleAny(Exception ex, HttpServletRequest request) {
		String path = request != null ? request.getRequestURI() : "N/A";
		log.error("Unhandled exception at {}", path, ex);
		return ResponseEntity.status(500).body(ApiError.builder().code("INTERNAL_ERROR").message("Internal server error").build()); // 500 일반 에러
	}
}


