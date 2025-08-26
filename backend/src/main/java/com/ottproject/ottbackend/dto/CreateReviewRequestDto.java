package com.ottproject.ottbackend.dto;

import com.ottproject.ottbackend.validation.ContentOrRatingRequired;
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
 * - aniId/content: 식별/본문
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

    // 평점은 분리된 Rating 엔티티로 이동
}
