package com.ottproject.ottbackend.exception;

import com.ottproject.ottbackend.entity.ViewingProfile;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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
 * - handleUnreadableRequest: 읽을 수 없는 본문·타입 불일치 → 400
 * - handleAny: 스프링 요청 오류(ErrorResponse 4xx) → 그 상태 코드, 그 외 → 500/Internal error 고정 응답
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
        HttpStatus status = ex.getStatusCode() instanceof HttpStatus
                ? (HttpStatus) ex.getStatusCode()
                : HttpStatus.BAD_REQUEST; // 상태 추출
        return ResponseEntity.status(status)
                .body(ApiError.builder().code("ERROR").message(ex.getReason()).build()); // 코드/메시지 응답
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("Validation error", ex);
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Validation error"); // 첫 에러 메시지
        return ResponseEntity.badRequest()
                .body(ApiError.builder().code("VALIDATION_ERROR").message(msg).build()); // 400 + 메시지
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
                .body(ApiError.builder()
                        .code("PROFILE_NOT_FOUND")
                        .message("프로필을 찾을 수 없습니다.")
                        .build());
    }

    @ExceptionHandler(ViewingProfileLimitExceededException.class)
    public ResponseEntity<ApiError> handleViewingProfileLimit(ViewingProfileLimitExceededException ex) {
        log.warn("시청 프로필 상한 초과: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.builder()
                        .code("PROFILE_LIMIT_EXCEEDED")
                        .message("프로필은 계정당 " + ViewingProfile.MAX_PER_ACCOUNT + "개까지 만들 수 있습니다.")
                        .build());
    }

    @ExceptionHandler(LastViewingProfileException.class)
    public ResponseEntity<ApiError> handleLastViewingProfile(LastViewingProfileException ex) {
        log.warn("마지막 시청 프로필 삭제 시도: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.builder()
                        .code("LAST_PROFILE")
                        .message("마지막 프로필은 삭제할 수 없습니다.")
                        .build());
    }

    /**
     * 본문을 읽을 수 없거나(깨진 JSON) 경로 변수·파라미터를 선언 타입으로 바꿀 수 없는 요청 → 400.
     * 두 예외는 아래 ErrorResponse 가 아니라서 상태 코드를 스스로 갖지 않는다. 원인은 요청 쪽에 있으므로
     * 500 으로 두면 서버 결함 신호가 오염된다. 파서 메시지에는 내부 타입명이 섞여 있어 바디에 싣지 않는다.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleUnreadableRequest(Exception ex, HttpServletRequest request) {
        log.warn("잘못된 요청 at {}: {}", pathOf(request), ex.getClass().getSimpleName());
        return clientError(HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception ex, HttpServletRequest request) {
        String path = pathOf(request);
        // 스프링이 요청 단계에서 던지는 오류(없는 메서드 405, 지원하지 않는 형식 415, 없는 경로 404 등)는
        // 상태 코드와 응답 헤더(405 의 Allow 등)를 예외가 직접 들고 있다. 이것까지 500 으로 바꾸면
        // 클라이언트 실수가 서버 결함처럼 보이고 ERROR 로그가 쌓인다. 5xx 를 뜻하는 것은 아래로 내려보낸다.
        if (ex instanceof ErrorResponse errorResponse
                && errorResponse.getStatusCode().is4xxClientError()) {
            log.warn(
                    "요청 오류 at {}: {} {}",
                    path,
                    errorResponse.getStatusCode().value(),
                    ex.getClass().getSimpleName());
            return clientError(errorResponse.getStatusCode(), errorResponse.getHeaders());
        }
        log.error("Unhandled exception at {}", path, ex);
        return ResponseEntity.status(500)
                .body(ApiError.builder()
                        .code("INTERNAL_ERROR")
                        .message("Internal server error")
                        .build()); // 500 일반 에러
    }

    private static String pathOf(HttpServletRequest request) {
        return request != null ? request.getRequestURI() : "N/A";
    }

    /** 요청 오류 응답. 코드는 상태 이름, 메시지는 표준 사유 문구만 싣는다(원본 예외 메시지 제외). */
    private static ResponseEntity<ApiError> clientError(HttpStatusCode statusCode, HttpHeaders headers) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String code = status != null ? status.name() : "CLIENT_ERROR";
        String message = status != null ? status.getReasonPhrase() : "Bad request";
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .body(ApiError.builder().code(code).message(message).build());
    }
}
