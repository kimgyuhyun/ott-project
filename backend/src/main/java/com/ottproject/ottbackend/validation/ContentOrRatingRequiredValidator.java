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

        // 생성 요청 DTO 처리: 현재는 content만 검증(평점은 Rating 엔티티로 분리)
        if (value instanceof CreateReviewRequestDto dto) {
            String content = dto.getContent();
            return content != null && !content.isBlank();
        }

        // 수정 요청 DTO 처리(의도: no-op 업데이트 방지)
        if (value instanceof com.ottproject.ottbackend.dto.UpdateReviewRequestDto dto) {
            String content = dto.getContent();
            // 업데이트 시에도 최소한 content가 있어야 의미가 있으므로 content만 검증
            return content != null && !content.isBlank();
        }

        // 그 외 타입은 이 제약의 대상 아님 -> 통과
        return true;
    }
}

