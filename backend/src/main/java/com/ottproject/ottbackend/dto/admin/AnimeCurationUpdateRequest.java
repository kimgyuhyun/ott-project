package com.ottproject.ottbackend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 애니 단건 큐레이션 수정 요청
 *
 * 큰 흐름
 * - 운영자가 고칠 수 있는 값만 담는다: 콘텐츠(제목/줄거리/이미지), 배지, 노출 여부.
 * - null 인 필드는 "그대로 둔다"를 뜻하는 부분 수정이다(AdminContentService 의 관례와 동일).
 *   따라서 이 API 로 값을 null 로 되돌릴 수는 없다.
 *
 * 필드 개요
 * - 콘텐츠: TMDB 보강이 쓰는 필드와 정확히 겹친다. 하나라도 실제로 바뀌면 curated 가 켜진다
 * - 배지: 수집 시 하드코딩/휴리스틱으로 정해진 값(예: isDub 을 평점으로 추측)이라 사람이 바로잡는다
 * - isActive: 사용자 목록 노출 여부
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnimeCurationUpdateRequest {

    // ===== 콘텐츠 (AnimeEnhancementService 가 덮어쓰는 필드와 동일 집합) =====
    private String title;
    private String titleEn;
    private String titleJp;
    private String synopsis;
    private String fullSynopsis;
    private String posterUrl;
    private String backdropUrl;

    // ===== 운영자 판단 배지 =====
    private Boolean isExclusive;
    private Boolean isPopular;
    private Boolean isNew;
    private Boolean isCompleted;
    private Boolean isSubtitle;
    private Boolean isDub;
    private Boolean isSimulcast;

    private Boolean isActive;
}
