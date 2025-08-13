// backend/src/main/java/com/ottproject/ottbackend/validation/HalfStepValidator.java
package com.ottproject.ottbackend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class HalfStepValidator // HalfStep 어노테이션 검증기
        implements ConstraintValidator<HalfStep, Double> {

    @Override
    public boolean isValid(Double value, // 검증 대상 값
                           ConstraintValidatorContext context) {
        if (value == null) { // null 은 다른 제약이 막지 않는 한 허용
            return true; // 클래스 레벨 제약과 함께 동작
        }
        double scaled = value * 2.0; // 0.5 단위 → 2배하면 정수
        return Math.floor(scaled) == scaled; // 소수부가 0인지 확인
    }
}