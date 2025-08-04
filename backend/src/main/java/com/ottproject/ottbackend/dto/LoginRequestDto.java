package com.ottproject.ottbackend.dto;

import lombok.Getter;
import lombok.Setter;

/*
로그인 요청을 위한 DTO
클라이언트에서 서버로 전송하는 로그인 정보
 */
@Getter
@Setter
public class LoginRequestDto {
    private String email; // 로그인할 이메일
    private String password; // 로그인할 비밀번호
}
