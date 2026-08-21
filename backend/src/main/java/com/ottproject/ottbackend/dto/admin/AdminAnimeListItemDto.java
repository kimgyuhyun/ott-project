package com.ottproject.ottbackend.dto.admin;

import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.enums.SyncOrigin;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 관리자 큐레이션 목록/상세 항목
 *
 * 큰 흐름
 * - 운영자가 큐레이션 판단에 필요한 필드만 노출한다(사용자향 AnimeListDto 와 목적이 다르다).
 * - 사용자 목록에는 보이지 않는 값(isActive, curated, malId)을 함께 보여줘야
 *   "왜 이 작품이 사용자에게 안 보이는가"를 이 화면에서 설명할 수 있다.
 *
 * 줄거리(synopsis/fullSynopsis)는 담지 않는다 — fullSynopsis 가 TEXT 라 목록 응답이 본문 덩어리가 된다.
 * 수정 폼은 AdminAnimeDetailDto(GET /{animeId})로 전체를 따로 받는다.
 *
 * 필드 개요
 * - id/title/titleEn/titleJp/posterUrl: 식별과 수정 대상
 * - status/year: 검색 조건과 짝을 이루는 메타
 * - isActive + 배지 7종: 운영자가 켜고 끄는 값(수집 시 휴리스틱으로 찍힌 값이라 목록에서 바로 보여야 한다)
 * - curated/syncOrigin: 이 행의 내력(운영자가 손댔는지, 어디서 들어왔는지)
 * - updatedAt: 마지막 변경 시각
 */
@Getter
@Builder
@AllArgsConstructor
public class AdminAnimeListItemDto {

    private final Long id;
    private final Long malId;
    private final String title;
    private final String titleEn;
    private final String titleJp;
    private final String posterUrl;
    private final AnimeStatus status;
    private final Integer year;
    private final Boolean isActive;
    private final Boolean isExclusive;
    private final Boolean isPopular;
    private final Boolean isNew;
    private final Boolean isCompleted;
    private final Boolean isSubtitle;
    private final Boolean isDub;
    private final Boolean isSimulcast;
    private final Boolean curated;
    private final SyncOrigin syncOrigin;
    private final LocalDateTime updatedAt;

    /**
     * 엔티티 → DTO 변환.
     * syncOrigin 은 저장된 값이 아니라 malId 유무에서 파생한다(전용 컬럼이 없다).
     */
    public static AdminAnimeListItemDto from(Anime anime) {
        return AdminAnimeListItemDto.builder()
                .id(anime.getId())
                .malId(anime.getMalId())
                .title(anime.getTitle())
                .titleEn(anime.getTitleEn())
                .titleJp(anime.getTitleJp())
                .posterUrl(anime.getPosterUrl())
                .status(anime.getStatus())
                .year(anime.getYear())
                .isActive(anime.getIsActive())
                .isExclusive(anime.getIsExclusive())
                .isPopular(anime.getIsPopular())
                .isNew(anime.getIsNew())
                .isCompleted(anime.getIsCompleted())
                .isSubtitle(anime.getIsSubtitle())
                .isDub(anime.getIsDub())
                .isSimulcast(anime.getIsSimulcast())
                .curated(anime.getCurated())
                .syncOrigin(anime.getMalId() != null ? SyncOrigin.JIKAN : SyncOrigin.MANUAL)
                .updatedAt(anime.getUpdatedAt())
                .build();
    }
}
