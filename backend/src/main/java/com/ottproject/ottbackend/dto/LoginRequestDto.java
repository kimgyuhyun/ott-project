package com.ottproject.ottbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/*
로그인 요청을 위한 DTO
클라이언트에서 서버로 전송하는 로그인 정보
 */
@Getter
@Setter
public class LoginRequestDto {
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    private String email; // 로그인할 이메일

    @NotBlank(message = "비밀번호는 필수입니다")
    private String password; // 로그인할 비밀번호
}
