package com.ottproject.ottbackend.exception;

/**
 * 중복 멱등 요청 예외
 *
 * 큰 흐름
 * - 멱등키 선삽입이 유니크 제약에 걸렸을 때 던진다. 같은 키의 요청을 다른 요청이 이미 처리 중이거나 처리했다는 뜻이다.
 * - 이 예외가 던져지면 그 트랜잭션은 롤백된다. 중복 요청은 아무것도 바꾸지 않는 것이 맞으므로 잃을 게 없다.
 * - 컨트롤러는 이 예외를 성공으로 흡수한다. 첫 요청이 이미 반영해 뒀으므로 클라이언트에게는 성공이다.
 *
 * 왜 DataIntegrityViolationException 을 그대로 쓰지 않는가
 * - {@link DuplicateWebhookEventException} 과 같은 이유다. 처리 도중에는 멱등키 말고도 제약 위반이 날 수 있고,
 *   그것까지 성공으로 삼키면 실패가 조용히 묻힌다. 멱등키 경합만 이 타입으로 좁혀서 구분한다.
 */
public class DuplicateIdempotentRequestException extends RuntimeException {

    public DuplicateIdempotentRequestException(String purpose, String key, Throwable cause) {
        super("이미 처리 중이거나 처리된 요청입니다 - purpose: " + purpose + ", key: " + key, cause);
    }
}
