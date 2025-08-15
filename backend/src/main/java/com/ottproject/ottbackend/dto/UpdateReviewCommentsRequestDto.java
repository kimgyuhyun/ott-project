package com.ottproject.ottbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 댓글 수정 요청 DTO
 */
@Getter
@Setter
@Builder
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
public class UpdateReviewCommentsRequestDto {
    
    @NotBlank(message = "내용은 필수입니다.")
    @Size(max = 1000, message = "내용은 최대 1000자입니다.")
    private String content; //수정할 내용
}
