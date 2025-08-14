package com.ottproject.ottbackend.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 표준 에러 응답 바디
 * - code: 에러 코드 식별자
 * - message: 사용자/클라이언트 표시용 메시지
 */
@Getter // 게터 생성
@Builder // 빌더 제공
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 모든 필드 생성자
public class ApiError {
    private String code; // 에러 코드
    private String message; // 에러 메시지
}


