package com.ottproject.ottbackend.dto;

import com.ottproject.ottbackend.enums.AuthProvider;
import com.ottproject.ottbackend.enums.UserRole;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 사용자 정보 응답 DTO
 *
 * 큰 흐름
 * - 사용자 식별/권한/상태/타임스탬프를 노출한다(민감정보 제외).
 *
 * 필드 개요
 * - id/email/name/role/authProvider
 * - emailVerified/enabled/createdAt/updatedAt
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
