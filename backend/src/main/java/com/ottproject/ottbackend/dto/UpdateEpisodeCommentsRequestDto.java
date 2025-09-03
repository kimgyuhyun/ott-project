package com.ottproject.ottbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 에피소드 댓글 수정 요청 DTO
 *
 * 큰 흐름
 * - 에피소드 댓글 본문을 수정할 때 사용한다.
 * - Bean Validation 으로 필수/길이를 검증한다.
 *
 * 필드 개요
 * - content: 수정할 댓글 본문
 */
@Getter // 게터 생성
@Setter // 세터 생성
@Builder // 빌더 생성
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
public class UpdateEpisodeCommentsRequestDto { // 에피소드 댓글 수정 요청 DTO 시작
    
    @NotBlank(message = "내용은 필수입니다.") // 공백 불가 검증
    @Size(max = 1000, message = "내용은 최대 1000자입니다.") // 길이 제한
    private String content; // 수정할 내용 본문
}
