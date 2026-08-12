package com.ottproject.ottbackend.exception;

/**
 * 시청 프로필을 찾지 못했을 때 던진다.
 *
 * 남의 프로필을 지목한 경우에도 이 예외를 쓴다. 권한 없음(403)으로 구분해 응답하면
 * "그 id 는 존재한다"는 사실이 새어 나가므로, 없는 것과 같게 취급한다.
 */
public class ViewingProfileNotFoundException extends RuntimeException {

    public ViewingProfileNotFoundException(String message) {
        super(message);
    }
}
