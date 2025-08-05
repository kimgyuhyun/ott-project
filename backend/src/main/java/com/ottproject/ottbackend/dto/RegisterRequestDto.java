package com.ottproject.ottbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/*
회원가입 요청을 위한 DTO
클라이언트에 서버로 전송하는 회원가입 정보
 */
@Getter
@Setter
public class RegisterRequestDto {
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    private String email; // 가입할 이메일

    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min =6, message = "비밀번호는 최소 6자 이상이어야 합니다")
    private String password; // 가입할 비밀번호

    @NotBlank(message = "이름은 필수입니다")
    @Size(min = 2, max = 20, message = "이름은 2자 이상 20자 이하여야 합니다")
    private String name; // 사용자 이름
}
