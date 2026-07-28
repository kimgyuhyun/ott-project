package com.ottproject.ottbackend.exception;

/**
 * 중복 웹훅 이벤트 예외
 *
 * 큰 흐름
 * - 멱등키 선삽입이 유니크 제약에 걸렸을 때 던진다. 같은 이벤트를 다른 요청이 이미 처리 중이거나 처리했다는 뜻이다.
 * - 컨트롤러는 이 예외만 200 으로 흡수한다. 결제사는 실패 응답을 받으면 재전송을 반복하므로
 *   중복 수신을 500 으로 돌려주면 루프가 된다.
 *
 * 왜 DataIntegrityViolationException 을 그대로 쓰지 않는가
 * - 웹훅 처리 중에는 멱등키 말고도 제약 위반이 날 수 있다(멤버십/구독 행 생성 등).
 *   그것까지 200 으로 삼키면 결제사가 성공으로 알고 재전송하지 않아 복구 기회가 사라진다.
 * - 그래서 멱등키 경합만 이 타입으로 좁혀서 구분한다.
 */
public class DuplicateWebhookEventException extends RuntimeException {

    public DuplicateWebhookEventException(String eventId, Throwable cause) {
        super("이미 처리 중이거나 처리된 웹훅 이벤트입니다 - eventId: " + eventId, cause);
    }
}
