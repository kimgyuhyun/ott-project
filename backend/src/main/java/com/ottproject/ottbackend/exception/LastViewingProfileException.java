package com.ottproject.ottbackend.exception;

/**
 * 마지막 하나 남은 시청 프로필을 지우려 할 때 던진다.
 *
 * 프로필이 0개인 계정은 선택 화면에서 아무것도 고를 수 없으므로 허용하지 않는다.
 */
public class LastViewingProfileException extends RuntimeException {

    public LastViewingProfileException(String message) {
        super(message);
    }
}
