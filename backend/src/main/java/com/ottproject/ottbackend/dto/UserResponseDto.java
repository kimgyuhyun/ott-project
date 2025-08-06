package com.ottproject.ottbackend.dto;

import com.ottproject.ottbackend.enums.AuthProvider;
import com.ottproject.ottbackend.enums.UserRole;
import lombok.*;

import java.time.LocalDateTime;

/*
사용자 정보 응답을 위한 DTO
서버에서 클라이언트로 전송하는 사용자 정보
비밀번호는 보안상 제외
 */
@Getter
@Setter
@Builder // 빌더 패턴 추가
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 모든 필드 생성자
public class UserResponseDto {
    private Long id; // 사용자 고유 ID
    private String email; // 이메일
    private String name; // 사용자 이름
    private UserRole role; // 사용자 권한
    private AuthProvider authProvider; // 인증 제공자
    private boolean emailVerified; // 이메일 인증 여부
    private boolean enabled; // 계정 활성화 여부
    private LocalDateTime createdAt; // 가입일시
    private LocalDateTime updatedAt; // 수정일시
}
