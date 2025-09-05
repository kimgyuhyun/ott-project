package com.ottproject.ottbackend.exception;

/**
 * 19금 콘텐츠 예외
 * 
 * 큰 흐름
 * - 19금 콘텐츠가 감지되었을 때 발생하는 예외
 * - 데이터 수집 과정에서 해당 콘텐츠를 제외하기 위해 사용
 */
public class AdultContentException extends RuntimeException {
    
    public AdultContentException(String message) {
        super(message);
    }
    
    public AdultContentException(String message, Throwable cause) {
        super(message, cause);
    }
}
