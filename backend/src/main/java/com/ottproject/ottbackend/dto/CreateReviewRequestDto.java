package com.ottproject.ottbackend.dto;

import com.ottproject.ottbackend.validation.ContentOrRatingRequired;
import com.ottproject.ottbackend.validation.HalfStep;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 리뷰 생성 요청 DTO
 *
 * 큰 흐름
 * - 대상 애니 ID는 필수, 내용과 평점은 선택이다.
 * - 커스텀 제약(ContentOrRatingRequired, HalfStep)을 통해 정책을 강제한다.
 *
 * 필드 개요
 * - aniId/content/rating: 식별/본문/평점
 */
@Getter
@Setter
@Builder
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
@Valid
@ContentOrRatingRequired
public class CreateReviewRequestDto {

    @NotNull(message = "aniId는 필수입니다.") // 필수 값
    private Long aniId; // 리뷰 대상 애니(목록) ID

    @Size(max = 1000, message = "내용은 최대 1000자입니다.") // 길이 제한
    private String content; // 리뷰 내용 (선택)

    @DecimalMin(value = "0.5", message = "평점은 0.5 이상이어야 합니다.") // 최소
    @DecimalMax(value = "5.0", message = "평점은 5.0 이하여야 합니다.") // 최대
    @HalfStep
    private Double rating; // 평점(선택)
}
