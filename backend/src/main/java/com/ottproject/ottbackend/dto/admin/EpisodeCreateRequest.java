package com.ottproject.ottbackend.dto.admin;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 관리자 에피소드 등록 요청
 *
 * 필드 개요
 * - episodeNumber: 화수(1 이상, 같은 작품 안에서 유일해야 한다)
 * - title: 에피소드 제목
 * - thumbnailUrl: 썸네일 URL
 * - videoUrl: 영상 URL
 * - duration: 재생 길이(초)
 */
@Getter
@Setter
@NoArgsConstructor
public class EpisodeCreateRequest {

    private Integer episodeNumber;
    private String title;
    private String thumbnailUrl;
    private String videoUrl;
    // Episode 엔티티에는 duration 컬럼이 없다. 팩토리가 값의 유효성(0 초과)만 검증하고 저장하지는 않는다.
    private Integer duration;
}
