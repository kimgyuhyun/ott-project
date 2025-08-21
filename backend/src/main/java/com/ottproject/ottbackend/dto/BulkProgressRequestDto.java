package com.ottproject.ottbackend.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 진행률 벌크 조회 요청 DTO
 *
 * 큰 흐름
 * - 여러 에피소드의 진행률을 한 번에 조회하기 위한 ID 목록을 전달한다.
 *
 * 필드 개요
 * - episodeIds: 에피소드 ID 목록(비어있으면 안 됨)
 */
@Getter // 접근자 생성
@Setter // 설정자 생성
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 모든 필드 생성자
public class BulkProgressRequestDto { // 진행률 벌크 조회 요청
    @NotEmpty // 비어 있으면 안 됨
    private List<Long> episodeIds; // 조회할 에피소드 ID 목록
}


