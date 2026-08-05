package com.ottproject.ottbackend.dto;

import com.ottproject.ottbackend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 내 프로필 응답
 *
 * 큰 흐름
 * - GET /api/users/me/profile 의 응답 본문이다.
 *
 * username 이 name 과 중복인 이유
 * - 프론트가 username 키를 읽는 화면이 있어 별칭으로 함께 내려준다.
 *   이전 구현이 Map 에 두 키를 같이 담고 있었고, 그 계약을 그대로 옮겼다.
 *
 * 필드 개요
 * - id/email/name/username: 식별과 표시명
 * - role/authProvider: 권한과 가입 경로(enum 이름 문자열)
 * - emailVerified/enabled: 계정 상태
 * - createdAt: 가입 시각
 */
@Getter
@Builder
@AllArgsConstructor
public class UserProfileDto {

    private final Long id;
    private final String email;
    private final String name;
    private final String username;
    private final String role;
    private final String authProvider;
    private final boolean emailVerified;
    private final boolean enabled;
    private final LocalDateTime createdAt;

    /**
     * 엔티티 → DTO 변환.
     *
     * @param user 사용자 엔티티
     * @return 응답용 DTO
     */
    public static UserProfileDto from(User user) {
        return UserProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .username(user.getName()) // 프론트 호환 별칭
                .role(user.getRole().name())
                .authProvider(user.getAuthProvider().name())
                .emailVerified(user.isEmailVerified())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
