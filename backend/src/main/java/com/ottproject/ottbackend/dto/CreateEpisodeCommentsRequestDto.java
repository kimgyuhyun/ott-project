package com.ottproject.ottbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*; // 임포트 라인에는 주석을 달지 않습니다.

/**
 * 에피소드 댓글 생성 요청 DTO
 *
 * 큰 흐름
 * - 최상위 댓글/대댓글 공통으로 본문(content)만 전달한다.
 * - 대댓글 여부는 경로/파라미터에서 식별한다.
 *
 * 필드 개요
 * - content: 댓글 본문
 */
@Getter // 게터 생성
@Setter // 세터 생성
@Builder // 빌더 생성
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
public class CreateEpisodeCommentsRequestDto { // 에피소드 댓글 생성 요청 DTO 선언
    @NotBlank(message = "내용은 필수입니다.") // 공백 불가 검증
    @Size(max = 1000, message = "내용은 최대 1000자 입니다.") // 길이 제한
    private String content; // 댓글 내용(최상위/대댓글 공통)
}
