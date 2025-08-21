package com.ottproject.ottbackend.validation;

import com.ottproject.ottbackend.dto.CreateReviewRequestDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * ContentOrRatingRequiredValidator
 *
 * 큰 흐름
 * - ContentOrRatingRequired의 실제 검증기. 생성/수정 DTO 모두 지원한다.
 *
 * 메서드 개요
 * - isValid: DTO 타입 분기 후 content/rating 둘 중 하나 존재 여부를 검사한다.
 */
public class ContentOrRatingRequiredValidator implements ConstraintValidator<ContentOrRatingRequired, Object> {
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) return true; // null 객체는 다른 @NotNull 에서 걸리도록 통과

        // 생성 요청 DTO 처리
        if (value instanceof CreateReviewRequestDto dto) {
            String content = dto.getContent();
            Double rating = dto.getRating();

            boolean hasContent = content != null && !content.isBlank(); // 공백은 미입력으로 간주
            boolean hasRating = rating != null; // null 이면 미입력

            return hasContent || hasRating; // 둘 중 하나만 있으면 통과
        }

        // 수정 요청 DTO 처리(의도: no-op 업데이트 방지)
        if (value instanceof com.ottproject.ottbackend.dto.UpdateReviewRequestDto dto) {
            String content = dto.getContent();
            Double rating = dto.getRating();

            boolean hasContent = content != null && !content.isBlank();
            boolean hasRating = rating != null;

            return hasContent || hasRating; // 둘 중 하나만 있으면 통과
        }

        // 그 외 타입은 이 제약의 대상 아님 -> 통과
        return true;
    }
}

