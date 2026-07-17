package com.ottproject.ottbackend.dto.admin;

import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.enums.SyncOrigin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 관리자 큐레이션 단건 상세 (수정 폼용)
 *
 * 목록(AdminAnimeListItemDto)과 나누는 이유
 * - fullSynopsis 는 TEXT 라 목록 20건에 실어 보내면 응답이 본문 덩어리가 된다.
 *   목록은 판단에 필요한 것만, 상세는 수정 가능한 전부를 담는다.
 *
 * 필드 개요
 * - 수정 가능: title/titleEn/titleJp, synopsis/fullSynopsis, posterUrl/backdropUrl, 배지 7종, isActive
 * - 읽기 전용 맥락: id/malId/status/year/curated/syncOrigin/updatedAt
 */
@Getter
@Builder
@AllArgsConstructor
public class AdminAnimeDetailDto {

    private final Long id;
    private final Long malId;

    // 콘텐츠 — TMDB 보강이 쓰는 필드와 겹친다(수정 시 curated 가 켜진다)
    private final String title;
    private final String titleEn;
    private final String titleJp;
    private final String synopsis;
    private final String fullSynopsis;
    private final String posterUrl;
    private final String backdropUrl;

    // 운영자 판단 배지 — 수집 시 하드코딩/휴리스틱으로 정해진 값이라 사람이 바로잡아야 한다
    private final Boolean isExclusive;
    private final Boolean isPopular;
    private final Boolean isNew;
    private final Boolean isCompleted;
    private final Boolean isSubtitle;
    private final Boolean isDub;
    private final Boolean isSimulcast;

    private final Boolean isActive;

    // 읽기 전용 맥락
    private final AnimeStatus status;
    private final Integer year;
    private final Boolean curated;
    private final SyncOrigin syncOrigin;
    private final LocalDateTime updatedAt;

    public static AdminAnimeDetailDto from(Anime anime) {
        return AdminAnimeDetailDto.builder()
                .id(anime.getId())
                .malId(anime.getMalId())
                .title(anime.getTitle())
                .titleEn(anime.getTitleEn())
                .titleJp(anime.getTitleJp())
                .synopsis(anime.getSynopsis())
                .fullSynopsis(anime.getFullSynopsis())
                .posterUrl(anime.getPosterUrl())
                .backdropUrl(anime.getBackdropUrl())
                .isExclusive(anime.getIsExclusive())
                .isPopular(anime.getIsPopular())
                .isNew(anime.getIsNew())
                .isCompleted(anime.getIsCompleted())
                .isSubtitle(anime.getIsSubtitle())
                .isDub(anime.getIsDub())
                .isSimulcast(anime.getIsSimulcast())
                .isActive(anime.getIsActive())
                .status(anime.getStatus())
                .year(anime.getYear())
                .curated(anime.getCurated())
                // 전용 컬럼이 아니라 malId 유무에서 파생한다(SyncOrigin 참고)
                .syncOrigin(anime.getMalId() != null ? SyncOrigin.JIKAN : SyncOrigin.MANUAL)
                .updatedAt(anime.getUpdatedAt())
                .build();
    }
}
