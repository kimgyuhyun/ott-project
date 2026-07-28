package com.ottproject.ottbackend.dto;

import com.ottproject.ottbackend.entity.User;
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
 *
 * 메서드 개요
 * - from: 엔티티→응답 DTO (다른 DTO 들과 같은 정적 팩토리 방식)
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

    /**
     * 엔티티 → 응답 DTO
     *
     * - password/providerId 등 민감·내부 필드는 애초에 이 DTO 에 없으므로 여기서 다루지 않는다.
     * - 역방향(응답 DTO → User)은 제공하지 않는다. 이 DTO 는 노출용 부분집합이라
     *   엔티티로 되돌리면 빠진 필드가 조용히 null 이 되거나 권한 필드를 덮어쓴다.
     */
    public static UserResponseDto from(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .authProvider(user.getAuthProvider())
                .emailVerified(user.isEmailVerified())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
