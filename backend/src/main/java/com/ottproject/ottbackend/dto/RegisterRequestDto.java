package com.ottproject.ottbackend.dto;

import lombok.Getter;
import lombok.Setter;

/*
회원가입 요청을 위한 DTO
클라이언트에 서버로 전송하는 회원가입 정보
 */
@Getter
@Setter
public class RegisterRequestDto {
    private String email; // 가입할 이메일
    private String password; // 가입할 비밀번호
    private String name; // 사용자 이름
}
