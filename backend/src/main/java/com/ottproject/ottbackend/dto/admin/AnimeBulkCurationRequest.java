package com.ottproject.ottbackend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 조건 기반 애니 벌크 큐레이션 요청
 *
 * 큰 흐름
 * - 검색 조건에 걸린 작품 전체에 같은 변경을 적용한다(예: 특정 연도 일괄 비활성화, 배지 일괄 해제).
 * - 제목/포스터는 일부러 뺐다. 여러 작품을 같은 제목으로 만드는 건 의미가 없다.
 *
 * 콘텐츠(제목/줄거리/이미지)는 대상이 아니다. 그래서 벌크는 curated 를 건드리지 않는다 —
 * 보강이 덮어쓰는 필드를 손대지 않으므로 보강에서 제외할 이유가 없다.
 *
 * 필드 개요
 * - condition: 대상 선정 조건. 비어 있으면 전체가 대상이 되므로 서비스가 거부한다
 * - 배지 7종 + isActive: 적용할 값(null = 유지)
 * - expectedCount: 미리보기에서 확인한 건수. 실제와 다르면 서비스가 409 로 중단한다
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnimeBulkCurationRequest {

    private AnimeCurationSearchCondition condition;

    private Boolean isActive;
    private Boolean isExclusive;
    private Boolean isPopular;
    private Boolean isNew;
    private Boolean isCompleted;
    private Boolean isSubtitle;
    private Boolean isDub;
    private Boolean isSimulcast;

    /**
     * 미리보기 시점에 운영자가 본 대상 건수.
     *
     * 실행 직전 다시 세어 이 값과 다르면 중단한다. 미리보기와 실행 사이에 동기화 배치가 행을 늘리거나
     * 다른 운영자가 조건에 걸리는 작품을 만들 수 있는데, 그러면 운영자가 승인한 것보다 많은 작품이 바뀐다.
     */
    private long expectedCount;

    /**
     * 적용할 변경이 하나라도 있는지.
     * 아무 값도 없으면 조건에 걸린 행을 훑기만 하고 아무것도 안 바꾸는 요청이라 거부한다.
     */
    public boolean hasAnyChange() {
        return isActive != null || isExclusive != null || isPopular != null || isNew != null
                || isCompleted != null || isSubtitle != null || isDub != null || isSimulcast != null;
    }
}
