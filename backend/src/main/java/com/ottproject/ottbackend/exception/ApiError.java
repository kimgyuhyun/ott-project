package com.ottproject.ottbackend.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ApiError
 *
 * 큰 흐름
 * - API 오류를 표준 형태(code, message)로 표현하는 응답 바디이다.
 *
 * 필드 개요
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


