package com.ottproject.ottbackend.dto.admin;

import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.enums.SyncOrigin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 관리자 애니 큐레이션 검색 조건
 *
 * 큰 흐름
 * - 운영자가 자유롭게 조합하는 검색 조건을 담는다. null 인 필드는 "이 조건은 걸지 않음"을 뜻한다.
 * - 검색과 벌크 수정이 같은 조건 객체를 쓴다. 미리보기에서 본 결과와 실제로 수정되는 대상이
 *   같은 조건에서 나와야 하기 때문이다.
 *
 * 필드 개요
 * - titleKeyword: 한/영/일 제목 통합 부분일치
 * - status/year: 방영 상태/연도
 * - isActive: 노출 여부(사용자 목록 쿼리가 전부 is_active=true 로 거른다)
 * - isExclusive/isPopular/isNew: 운영자가 직접 켜고 끄는 배지
 * - curated: 운영자가 이미 손댄 작품인지
 * - syncOrigin: 유입 경로(malId 유무에서 파생)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnimeCurationSearchCondition {

    private String titleKeyword;    // title/titleEn/titleJp 부분일치(대소문자 무시)
    private AnimeStatus status;     // ONGOING/COMPLETED/UPCOMING/HIATUS
    private Integer year;
    private Boolean isActive;
    private Boolean isExclusive;
    private Boolean isPopular;
    private Boolean isNew;
    private Boolean curated;
    private SyncOrigin syncOrigin;  // JIKAN(malId 있음) / MANUAL(malId 없음)

    /**
     * 조건이 하나도 걸리지 않았는지 여부.
     *
     * 벌크 수정의 안전장치다 — 빈 조건은 곧 "전체 애니"를 뜻하므로, 조건 없는 벌크는 거부한다.
     * 검색에서는 빈 조건이 정상이며 전체 목록을 의미한다.
     *
     * 공백뿐인 titleKeyword 는 조건이 아니다. 실수로 넘어온 " " 가 조건 검사를 통과해
     * 전체 수정을 열어주면 안 된다.
     */
    public boolean isEmpty() {
        return !hasTitleKeyword()
                && status == null
                && year == null
                && isActive == null
                && isExclusive == null
                && isPopular == null
                && isNew == null
                && curated == null
                && syncOrigin == null;
    }

    public boolean hasTitleKeyword() {
        return titleKeyword != null && !titleKeyword.trim().isEmpty();
    }
}
