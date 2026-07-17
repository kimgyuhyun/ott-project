package com.ottproject.ottbackend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 애니 단건 큐레이션 수정 요청
 *
 * 큰 흐름
 * - 운영자가 고칠 수 있는 값만 담는다: 제목(한/영/일), 포스터, 배지, 노출 여부.
 * - null 인 필드는 "그대로 둔다"를 뜻하는 부분 수정이다(AdminContentService 의 관례와 동일).
 *   따라서 이 API 로 값을 null 로 되돌릴 수는 없다. 제목/포스터를 비우는 건 큐레이션 시나리오가 아니다.
 *
 * 필드 개요
 * - title/titleEn/titleJp/posterUrl: 콘텐츠 필드. 이 중 하나라도 실제로 바뀌면 curated 가 켜진다
 * - isExclusive/isPopular/isNew: 운영자 판단 배지
 * - isActive: 사용자 목록 노출 여부
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnimeCurationUpdateRequest {

    private String title;
    private String titleEn;
    private String titleJp;
    private String posterUrl;

    private Boolean isExclusive;
    private Boolean isPopular;
    private Boolean isNew;
    private Boolean isActive;

    /**
     * TMDB 자동 보강이 덮어쓰는 필드를 건드리는 요청인지 여부.
     *
     * curated 는 "보강이 이 작품을 건너뛰어야 한다"는 뜻이므로, 보강이 손대지도 않는 배지만 바꿨을 때
     * 켜면 과잉이다(그 작품의 제목 보강이 영구히 막힌다). 콘텐츠 필드를 건드릴 때만 의미가 있다.
     */
    public boolean touchesContentFields() {
        return title != null || titleEn != null || titleJp != null || posterUrl != null;
    }
}
