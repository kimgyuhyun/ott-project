package com.ottproject.ottbackend.exception;

/**
 * 인증 코드 하나에 허용된 입력 시도 횟수를 넘겼을 때 던진다.
 *
 * 코드는 이미 폐기됐으므로 같은 요청을 다시 보내도 성공하지 않는다. 새 코드를 받는 것이 다음 행동이다.
 */
public class VerificationAttemptsExceededException extends RuntimeException {

    public VerificationAttemptsExceededException() {
        super("verification code attempts exceeded");
    }
}
