package com.ottproject.ottbackend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented // javadoc 포함
@Constraint(validatedBy = HalfStepValidator.class) // 이 어노테이션의 검증기 연결
@Target({ ElementType.FIELD, ElementType.PARAMETER })  // 필드/파라미터에 부착 가능
@Retention(RetentionPolicy.RUNTIME) // 런타임까지 유지
public @interface HalfStep { // 0.5 단위인지 확인하는 제약

    String message() default "값은 0.5 단위여야 합니다."; // 기본 실패 메시지

    Class<?>[] groups() default {}; // 표준 속성: 그룹
    Class<? extends Payload>[] payload() default {}; // 표준 속성: 페이로드
}