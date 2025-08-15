package com.ottproject.ottbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*; // 임포트 라인에는 주석을 달지 않습니다.

/**
 * 댓글 생성 요청 DTO
 * - 최상위 댓글/대댓글 모두 공통으로 content 만 전달
 */
@Getter // 게터 생성
@Setter // 세터 생성
@Builder // 빌더 생성
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
public class CreateCommentRequestDto { // 댓글 생성 요청 DTO 선언
    @NotBlank(message = "내용은 필수입니다.") // 공백 불가 검증
    @Size(max = 1000, message = "내용은 최대 1000자 입니다.") // 길이 제한
    private String content; // 댓글 내용(최상위/대댓글 공통)
}