package com.ottproject.ottbackend.validation;

import com.ottproject.ottbackend.dto.CreateReviewRequestDto;
import com.ottproject.ottbackend.dto.UpdateReviewRequestDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * ContentOrRatingRequired 에노테이션의 실제 검증 로직
 * - create/update 두 DTO 모두 지원
 */
public class ContentOrRatingRequiredValidator implements ConstraintValidator<ContentOrRatingRequired, Object> {
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) return true; // null 객체는 다른 @NotNUll 에서 걸리도록 통과

        // 생성 요청 DTO 처리
        if (value instanceof CreateReviewRequestDto dto) {
            String content = dto.getContent();
            Double rating = dto.getRating();

            boolean hasContent = content != null && !content.isBlank(); // 공백은 미입력으로 간주
            boolean hasRating = rating != null; // null 이면 미입력

            return hasContent || hasRating; // 둘 중 하나만 있으면 통과
        }

        // 수정 요청 DTO 처리(의도: no-op 업데이트 방지)
        if (value instanceof UpdateReviewRequestDto dto) {
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

