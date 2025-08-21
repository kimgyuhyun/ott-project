package com.ottproject.ottbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 비밀번호 변경 요청 DTO
 *
 * 큰 흐름
 * - 현재 비밀번호와 새 비밀번호를 받아 변경한다.
 * - Bean Validation 으로 필수/길이를 검증한다.
 *
 * 필드 개요
 * - currentPassword/newPassword: 비밀번호 쌍
 */
@Getter
@Setter
public class ChangePasswordRequestDto {

    @NotBlank(message = "현재 비밀번호는 필수입니다.") // 현재 비밀번호 필수 검증
    private String currentPassword; // 현재 비밀번호

    @NotBlank(message = "새 비밀번호는 필수입니다.") // 새 비밀번호 필수 검증
    @Size(min = 6, message = "새 비밀번호는 최소 6자 이상이어야 합니다.") // 새 비밀번호 길이 검증
    private String newPassword; // 새 비밀번호
}
