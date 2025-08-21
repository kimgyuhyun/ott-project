package com.ottproject.ottbackend.validation;

import jakarta.validation.Constraint; // 제약 조건 메타 애노테이션
import jakarta.validation.Payload;    // 페이로드(그룹/메타정보) 지정용
import java.lang.annotation.*;        // 표준 애노테이션 메타

/**
 * ContentOrRatingRequired
 *
 * 큰 흐름
 * - 리뷰 생성/수정 요청에서 content 또는 rating 둘 중 하나는 반드시 채워지도록 강제하는 클래스 레벨 제약이다.
 * - content 가 공백/빈문자열이면 미입력으로, rating 이 null 이면 미입력으로 간주한다.
 *
 * 요소 개요
 * - message: 기본 에러 메시지
 * - groups: Bean Validation 그룹
 * - payload: 메타정보 페이로드
 */
@Documented // javadoc 에 포함
@Target(ElementType.TYPE) // 클래스 레벨에만 적용
@Retention(RetentionPolicy.RUNTIME) // 런타임 유지
@Constraint(validatedBy = ContentOrRatingRequiredValidator.class) // 실제 검증 로직 연결
public @interface ContentOrRatingRequired {

    String message() default "내용 또는 평점 중 하나는 반드시 입력해야 합니다."; // 기본 에러 메시지

    Class<?>[] groups() default {}; // Bean validation 그룹 지정
    Class<? extends Payload>[] payload() default {}; // 메타정보 전달용 페이로드
}
