package com.ottproject.ottbackend.dto;

import com.ottproject.ottbackend.enums.AnimeStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 상세 화면(상단 히어로 + 탭) 전용 DTO
 * - 헤더: 제목/포스터/평점/연령/상태/배지
 * - 개요: 요약/전체 시놉시스(더보기)
 * - 제작/원작/언어/국가
 * - 방영 정보: 요일/시간/시즌/연도/타입/러닝타임
 * - 집계: 총/현재 에피소드 수
 * - 연관: 장르/제작사/에피소드 목록
 *
 * (헤더 + 더보기 팝업 + 에피소드 탭까지 한 번에 제공)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AniDetailDto {

    private Long aniId; // AniLIst.id (목록 식별자
    private Long detailId; // aniDetail.id (상세 식별자)

    private String title; // 제목(한글)
    private String titleEn; // 제목(영문)
    private String titleJp; // 제목(일문)
    private String posterUrl; // 포스터 이미지 URL

    // 평점/등급/상태
    private Double rating; // 평균 평점 (0.0 ~ 5.0)
    private Integer ratingCount; // 평가 수;
    private String ageRating; // 연령 등급
    private AnimeStatus animeStatus; // 방영 상태

    private Boolean isCompleted; // 완결 여뷰
    private Boolean isExclusive; // 독점 여부
    private Boolean isPopular; // 인기작 여부
    private Boolean isNew; // 신작
    private Boolean isSubtitle; // 자막
    private Boolean isDub; // 더빙
    private Boolean isSimulcast; // 동시 방영
    private Boolean isActive; // 활성화

    // 개요/상세 설명
    private String fullSynopsis; // 전체 줄거리
    private List<String> tags; // 태그 목록(예: #가족, #일상)
    private String voiceActors; // 성우 정보 원문(JSON/Text)  // 필요 시 파싱하여 화면 바인딩

    // 방영 정보
    private LocalDate releaseDate; // 방영 시작일
    private LocalDate endDate; // 방영 종료일(null 가능)
    private String broadcastDay; // 방영 요일(월~일)
    private String broadcastTime; // 방영 시간
    private String season; // 시즌(봄/여름/가을/겨울)
    private Integer year; // 방영 연도
    private String type; // 타입(TV/극장판/OVA 등)
    private Integer duration; // 에피소드당 러닝타임(분)
    private String releaseQuarter; // 출시 분기(예: 2019-3분기)
    private String source; // 원작(만화/라노벨/오리지널 등)
    private String country; // 제작국
    private String language; // 언어
    private String director; // 감독

    // 에피소드 집계
    private Integer totalEpisodes; // 총 에피소드(AniList.total_episodes 에서 조인)
    private Integer currentEpisodes; // 현재 업로드 화수
    
    // 연관 목록
    private List<GenreSimpleDto> genres; // 장르 목록(뱃지)
    private List<StudioSimpleDto> studios; // 제작사 목록
    private List<EpisodeDto> episodes; // 에피소드 목록(Episode 엔티티 기반)
    
    // 타임 스탬프 상세 기준
    private LocalDateTime createdAt; // 생성일시
    private LocalDateTime updatedAt; // 수정일시

    private Boolean isFavorited; // 현재 사용자 기준 찜 여부(true = 찜됨, false = 찜안됨) 비로그인 시 기본 false
}
