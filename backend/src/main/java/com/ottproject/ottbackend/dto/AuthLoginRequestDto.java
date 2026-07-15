package com.ottproject.ottbackend.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 로그인 요청 DTO
 *
 * 큰 흐름
 * - 이메일/비밀번호를 받아 인증을 시도한다.
 * - Bean Validation 으로 형식/필수값을 검증한다.
 *
 * 필드 개요
 * - email/password: 로그인 자격 증명
 */
@Getter
@Setter
public class AuthLoginRequestDto {
	@jakarta.validation.constraints.NotBlank(message = "이메일은 필수입니다")
	@jakarta.validation.constraints.Email(message = "올바른 이메일 형식이 아닙니다")
	private String email; // 로그인할 이메일

	@jakarta.validation.constraints.NotBlank(message = "비밀번호는 필수입니다")
	private String password; // 로그인할 비밀번호

	// Cloudflare Turnstile 토큰(선택): 직전 로그인 실패로 사람 확인이 요구될 때만 필요.
	// 정상 첫 로그인에는 없어도 되므로 검증 애너테이션을 붙이지 않는다.
	private String turnstileToken;
}
