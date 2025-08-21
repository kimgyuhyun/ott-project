package com.ottproject.ottbackend.exception;

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
 * - handleAny: 기타 예외 → 500/Internal error 고정 응답
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

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleAny(Exception ex, HttpServletRequest request) {
		String path = request != null ? request.getRequestURI() : "N/A";
		log.error("Unhandled exception at {}", path, ex);
		return ResponseEntity.status(500).body(ApiError.builder().code("INTERNAL_ERROR").message("Internal server error").build()); // 500 일반 에러
	}
}


