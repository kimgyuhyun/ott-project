package com.ottproject.ottbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 리뷰 수정 요청 DTO
 * - 경로변수로 reviewId, 바디에는 수정 필드만 전달
 */
@Getter
@Setter
@Builder
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
@Valid
public class UpdateReviewRequestDto {

    @Size(max = 1000, message = "내용은 최대 1000자입니다.")
    private String content; // 수정할 내용(선택)

    @DecimalMin(value = "0.5", message = "평정믄 0.5 이상이어야 합니다.")
    @DecimalMax(value = "5.0", message = "평점은 5.0 이하여야 합니다.")
    private Double rating; // 수정할 평점(선택)
}
