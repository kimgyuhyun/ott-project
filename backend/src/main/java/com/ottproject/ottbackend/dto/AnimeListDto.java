package com.ottproject.ottbackend.dto;

import lombok.*;

/**
 * 목록 화면(카드 그리드) 전용 DTO
 *
 * 큰 흐름
 * - 카드 그리드에서 필요한 최소 정보를 담는다.
 * - 배지 노출과 정렬용 메타를 포함한다.
 *
 * 필드 개요
 * - aniId/title/posterUrl: 식별/제목/썸네일
 * - rating/ratingCount: 평점/투표수
 * - isDub/isSubtitle/isExclusive/isNew/isPopular/isCompleted: 배지용 플래그
 * - animeStatus/year/type: 선택 노출용 메타
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnimeListDto {

	private Long aniId; // 애니 ID (목록용 기본 식별자)
	private String title; // 제목(한글)
	private String posterUrl; // 포스터 이미지 URL

	private Double rating; // 평점 (0.5 ~ 5.0)
	private Integer ratingCount; // 평가 수

	// 배지/노출 플래그
	private Boolean isDub; // 더빙 제공 여부
	private Boolean isSubtitle; // 자막 제공 여부
	private Boolean isExclusive; // 독점 여부
	private Boolean isNew; // 신작 여부
	private Boolean isPopular; // 인기작 여부
	private Boolean isCompleted; // 완결 여부

	// 선택 노출용
	private com.ottproject.ottbackend.enums.AnimeStatus animeStatus; // 방영 상태
	private Integer year; // 방영 연도
	private String type; // 타입(TV/극장판 등)
}
