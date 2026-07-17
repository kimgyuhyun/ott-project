package com.ottproject.ottbackend.enums;

/**
 * 애니메이션 데이터의 유입 경로
 *
 * 큰 흐름
 * - 관리자 큐레이션 검색에서 "어디서 들어온 데이터인가"로 거르기 위한 구분값이다.
 *
 * 주의: DB 컬럼이 아니라 malId 유무에서 파생되는 값이다.
 * - Anime 에는 동기화 출처를 담는 필드가 없다. malId(Jikan 식별자)만 있고 TMDB id 는 저장하지 않는다.
 *   (엔티티의 source 필드는 원작 매체를 뜻하므로 무관하다)
 * - 따라서 지금 데이터로 표현 가능한 구분은 "Jikan 이 준 것"과 "그렇지 않은 것" 둘뿐이다.
 *
 * 상수 개요
 * - JIKAN: malId 가 있는 작품(외부 동기화로 유입)
 * - MANUAL: malId 가 없는 작품(수동 등록/시드 데이터)
 */
public enum SyncOrigin {
    JIKAN,  // malId is not null
    MANUAL  // malId is null
}
