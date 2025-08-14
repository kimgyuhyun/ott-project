package com.ottproject.ottbackend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 전역 예외 핸들러
 * - 표준 에러 포맷으로 변환하여 응답
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiError> handleRse(ResponseStatusException ex) {
		HttpStatus status = ex.getStatusCode() instanceof HttpStatus ? (HttpStatus) ex.getStatusCode() : HttpStatus.BAD_REQUEST; // 상태 추출
		return ResponseEntity.status(status).body(ApiError.builder().code("ERROR") .message(ex.getReason()).build()); // 코드/메시지 응답
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
		String msg = ex.getBindingResult().getFieldErrors().stream()
				.findFirst().map(err -> err.getField() + ": " + err.getDefaultMessage()).orElse("Validation error"); // 첫 에러 메시지
		return ResponseEntity.badRequest().body(ApiError.builder().code("VALIDATION_ERROR").message(msg).build()); // 400 + 메시지
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleAny(Exception ex) {
		return ResponseEntity.status(500).body(ApiError.builder().code("INTERNAL_ERROR").message("Internal server error").build()); // 500 일반 에러
	}
}


