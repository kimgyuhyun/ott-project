package com.ottproject.ottbackend.exception;

/**
 * 계정당 시청 프로필 상한을 넘겨 만들려 할 때 던진다.
 *
 * 요청 형식은 올바르고 현재 상태 때문에 거절되는 것이라 충돌(409)로 매핑한다.
 */
public class ViewingProfileLimitExceededException extends RuntimeException {

    public ViewingProfileLimitExceededException(String message) {
        super(message);
    }
}
