package com.ottproject.ottbackend.dto;

import com.ottproject.ottbackend.validation.ContentOrRatingRequired;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 리뷰 수정 요청 DTO
 *
 * 큰 흐름
 * - 경로변수로 reviewId 를 받고, 바디에는 변경할 필드만 전달한다.
 * - 커스텀 제약(ContentOrRatingRequired, HalfStep)으로 정책을 강제한다.
 *
 * 필드 개요
 * - content: 수정 대상(선택)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
@Valid
@ContentOrRatingRequired
public class UpdateReviewRequestDto {

    @Size(max = 1000, message = "내용은 최대 1000자입니다.")
    private String content; // 수정할 내용(선택)

    // 평점은 분리된 Rating 엔티티로 이동
}
