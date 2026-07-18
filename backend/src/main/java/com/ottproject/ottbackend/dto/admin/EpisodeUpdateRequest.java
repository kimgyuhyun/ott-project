package com.ottproject.ottbackend.dto.admin;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 관리자 에피소드 수정 요청 (부분 수정)
 *
 * 전달하지 않은(null) 필드는 바꾸지 않는다. 값을 비우고 싶어도 빈 문자열은 허용하지 않는다 —
 * title/thumbnailUrl/videoUrl 은 not-null 컬럼이라 빈 값이 들어가면 재생이 깨진다.
 *
 * episodeNumber 는 일부러 뺐다. 시청 기록·진행률·댓글이 에피소드에 붙어 있어서 화수를 바꾸면
 * 이미 쌓인 데이터의 의미가 어긋난다. 순서를 고쳐야 하면 삭제 후 재등록이 안전하다.
 *
 * 필드 개요
 * - title/thumbnailUrl/videoUrl: 미디어 메타데이터
 * - isActive: 활성화 여부
 * - isReleased: 공개 여부
 */
@Getter
@Setter
@NoArgsConstructor
public class EpisodeUpdateRequest {

    private String title;
    private String thumbnailUrl;
    private String videoUrl;
    private Boolean isActive;
    private Boolean isReleased;
}
