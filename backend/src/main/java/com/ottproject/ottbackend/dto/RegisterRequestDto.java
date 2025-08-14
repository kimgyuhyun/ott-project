package com.ottproject.ottbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원가입 요청 DTO
 * - email/password/name 전달
 */
@Getter
@Setter
public class RegisterRequestDto {
	@jakarta.validation.constraints.NotBlank(message = "이메일은 필수입니다")
	@jakarta.validation.constraints.Email(message = "올바른 이메일 형식이 아닙니다")
	private String email; // 가입할 이메일

	@jakarta.validation.constraints.NotBlank(message = "비밀번호는 필수입니다")
	@jakarta.validation.constraints.Size(min =6, message = "비밀번호는 최소 6자 이상이어야 합니다")
	private String password; // 가입할 비밀번호

	@jakarta.validation.constraints.NotBlank(message = "이름은 필수입니다")
	@jakarta.validation.constraints.Size(min = 2, max = 20, message = "이름은 2자 이상 20자 이하여야 합니다")
	private String name; // 사용자 이름
}
