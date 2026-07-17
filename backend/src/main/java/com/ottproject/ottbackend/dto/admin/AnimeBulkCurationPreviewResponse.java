package com.ottproject.ottbackend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 벌크 큐레이션 미리보기 응답
 *
 * 큰 흐름
 * - 실제로 바꾸기 전에 "몇 건이 바뀌는지"와 "어떤 것들인지"를 보여준다.
 * - 조건 하나를 빠뜨려 수천 건을 건드리는 사고를 실행 전에 알아채게 하는 것이 목적이다.
 *
 * 필드 개요
 * - affectedCount: 조건에 걸린 전체 건수. 이 값을 벌크 요청의 expectedCount 로 되돌려 보내야 한다
 * - sample: 조건이 의도대로인지 눈으로 확인할 상위 몇 건
 */
@Getter
@AllArgsConstructor
public class AnimeBulkCurationPreviewResponse {

    private final long affectedCount;
    private final List<AdminAnimeListItemDto> sample;
}
