package com.ottproject.ottbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 댓글 생성 요청 DTO
 * - 리뷰에 대한 댓글 또는 특정 댓글의 대댓글 생성
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCommentRequestDto {

    @NotNull(message = "reviewId는 필수입니다.")
    private Long reviewId; // 댓글이 달릴 리뷰 ID

    private Long parentId; // 대댓글이면 부모 댓글 ID(최상위면 null)

    @NotBlank(message = "내용은 필수입니다.")
    @Size(max = 1000, message = "내용은 최대 1000자 입니다.")
    private String content; // 댓글 내용
}
